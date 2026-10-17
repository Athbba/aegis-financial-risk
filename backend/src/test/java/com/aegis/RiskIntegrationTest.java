package com.aegis;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aegis.auth.*;
import com.aegis.portfolio.*;
import com.aegis.risk.*;
import com.aegis.risk.RiskEngine.*;
import com.aegis.shared.DomainException;
import com.aegis.trading.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RiskIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired PortfolioService portfolios;
  @Autowired TradingService trading;
  @Autowired RiskService risk;
  @Autowired MockMvc mvc;

  static BigDecimal d(String s) {
    return new BigDecimal(s);
  }

  static LocalDate date(String s) {
    return LocalDate.parse(s);
  }

  record Fixture(UUID user, String email, UUID portfolio) {}

  Fixture fixture() {
    String email = UUID.randomUUID() + "@example.test";
    var user =
        users.saveAndFlush(new AppUser(UUID.randomUUID(), email, "test-no-login", Instant.now()));
    return new Fixture(
        user.getId(), email, portfolios.create(user.getId(), "Risk test", d("1000")).id());
  }

  void buy(Fixture f, String symbol, String quantity, String day) {
    trading.execute(
        f.user(),
        f.portfolio(),
        UUID.randomUUID().toString(),
        new TradingService.Request(symbol, TradingService.Side.BUY, d(quantity), date(day)));
  }

  @BeforeEach
  void prices() {
    for (var symbol : List.of("RSKA", "RSKB")) {
      jdbc.update(
          "insert into asset values(?,?,'Test','USD','phase3-fixture') on conflict do nothing",
          symbol,
          symbol);
      jdbc.update(
          "insert into historical_price(asset_symbol,price_date,close_price) select ?,d::date,?"
              + " from generate_series('2022-01-03'::date,'2025-12-31'::date,'1 day') d where"
              + " extract(isodow from d)<6 on conflict do nothing",
          symbol,
          symbol.equals("RSKA") ? d("100") : d("20"));
    }
  }

  @Test
  void fullWindowVolatilityVarCashWeightsAndDedicatedEndpoints() throws Exception {
    var f = fixture();
    buy(f, "RSKA", "3", "2022-01-03");
    buy(f, "RSKB", "10", "2022-01-03");
    var s = risk.summary(f.user(), f.portfolio(), date("2025-12-31"), 1461);
    assertThat(s.analysis().returns().points()).hasSize(1042);
    assertThat(s.analysis().dailyVolatility().value()).isZero();
    assertThat(s.analysis().var95().usd()).isZero();
    assertThat(s.analysis().var99().usd()).isZero();
    assertThat(s.analysis().concentration()).isEqualByComparingTo(".13");
    assertThat(s.drawdown().percent()).isZero();
    for (String endpoint : List.of("summary", "returns", "drawdown"))
      mvc.perform(
              get("/api/portfolios/" + f.portfolio() + "/risk/" + endpoint).with(user(f.email())))
          .andExpect(status().isOk());
  }

  @Test
  void selectedDateExcludesFutureTradesAndPrices() {
    var f = fixture();
    buy(f, "RSKA", "3", "2024-01-02");
    var before = risk.summary(f.user(), f.portfolio(), date("2024-01-05"), 730);
    trading.execute(
        f.user(),
        f.portfolio(),
        "future-sale",
        new TradingService.Request("RSKA", TradingService.Side.SELL, d("2"), date("2024-01-08")));
    jdbc.update(
        "update historical_price set close_price=9999 where asset_symbol='RSKA' and"
            + " price_date='2024-01-09'");
    try {
      assertThat(risk.summary(f.user(), f.portfolio(), date("2024-01-05"), 730)).isEqualTo(before);
    } finally {
      jdbc.update(
          "update historical_price set close_price=100 where asset_symbol='RSKA' and"
              + " price_date='2024-01-09'");
    }
    assertThat(
            risk.summary(f.user(), f.portfolio(), date("2024-01-01"), 730)
                .analysis()
                .var99()
                .status())
        .isEqualTo("CASH_ONLY");
  }

  @Test
  void historicalWeekdayGapInvalidatesRiskAndDrawdownButWeekendsDoNot() {
    var f = fixture();
    buy(f, "RSKA", "3", "2024-01-02");
    jdbc.update(
        "delete from historical_price where asset_symbol='RSKA' and price_date='2024-01-04'");
    try {
      var s = risk.summary(f.user(), f.portfolio(), date("2024-01-08"), 7);
      assertThat(s.analysis().returns().missingPairs()).isEqualTo(2);
      assertThat(s.analysis().dailyVolatility().status()).isEqualTo("MISSING_HISTORY");
      assertThat(s.drawdown().status()).isEqualTo("MISSING_VALUATIONS");
      assertThat(s.drawdown().missingDates()).containsExactly(date("2024-01-04"));
    } finally {
      jdbc.update(
          "insert into historical_price(asset_symbol,price_date,close_price)"
              + " values('RSKA','2024-01-04',100)");
    }
    var full = risk.summary(f.user(), f.portfolio(), date("2024-01-08"), 7);
    assertThat(full.analysis().returns().points()).hasSize(4);
    assertThat(full.drawdown().status()).isEqualTo("COMPLETE");
    assertThat(
            risk.summary(f.user(), f.portfolio(), date("2024-01-06"), 7)
                .analysis()
                .var95()
                .status())
        .isEqualTo("MISSING_PRICES");
  }

  Map<String, String> financialState(UUID id) {
    Map<String, String> state = new TreeMap<>();
    for (String table :
        List.of("portfolio", "executed_order", "position_lot", "cash_ledger", "audit_event")) {
      String key = table.equals("portfolio") ? "id" : "portfolio_id";
      state.put(
          table,
          jdbc.queryForObject(
              "select coalesce(jsonb_agg(to_jsonb(t) order by id)::text,'[]') from "
                  + table
                  + " t where "
                  + key
                  + "=?",
              String.class,
              id));
    }
    state.put(
        "allocations",
        jdbc.queryForObject(
            "select coalesce(jsonb_agg(to_jsonb(a) order by a.id)::text,'[]') from sell_allocation"
                + " a join executed_order o on o.id=a.sell_order_id where o.portfolio_id=?",
            String.class,
            id));
    return state;
  }

  @Test
  void stressMatchesManualFixtureAndNeverMutatesAnyFinancialRows() throws Exception {
    var f = fixture();
    buy(f, "RSKA", "3", "2024-01-02");
    buy(f, "RSKB", "10", "2024-01-02");
    var before = financialState(f.portfolio());
    String body =
        """
        {"date":"2024-01-05","scenarios":[{"name":"Mixed","commonShockPercent":"-10","overrides":{"RSKB":"20"}}]}
        """;
    mvc.perform(
            post("/api/portfolios/" + f.portfolio() + "/risk/stress")
                .with(user(f.email()))
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].stressedValue").value("1010.00"))
        .andExpect(jsonPath("$[0].gainLoss").value("10.00"));
    assertThat(financialState(f.portfolio())).isEqualTo(before);
    mvc.perform(
            post("/api/portfolios/" + f.portfolio() + "/risk/stress")
                .with(user(f.email()))
                .with(csrf())
                .contentType("application/json")
                .content(body.replace("-10", "-101")))
        .andExpect(status().isUnprocessableEntity());
    assertThat(financialState(f.portfolio())).isEqualTo(before);
  }

  @Test
  void ownershipAuthenticationAndCsrfApplyToEveryRiskEndpoint() throws Exception {
    var owner = fixture();
    var other = fixture();
    for (String endpoint : List.of("summary", "returns", "drawdown")) {
      String url = "/api/portfolios/" + owner.portfolio() + "/risk/" + endpoint;
      mvc.perform(get(url).with(user(other.email()))).andExpect(status().isNotFound());
      mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }
    String url = "/api/portfolios/" + owner.portfolio() + "/risk/stress";
    String body =
        "{\"date\":\"2024-01-05\",\"scenarios\":[{\"name\":\"Zero\",\"commonShockPercent\":0,\"overrides\":{}}]}";
    mvc.perform(
            post(url)
                .with(user(other.email()))
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isNotFound());
    mvc.perform(post(url).with(user(owner.email())).contentType("application/json").content(body))
        .andExpect(status().isForbidden());
    mvc.perform(post(url).with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void validatesDatesWindowsAndScenarioBoundsAndExposesInsufficiency() throws Exception {
    var f = fixture();
    buy(f, "RSKA", "3", "2024-01-02");
    var s = risk.summary(f.user(), f.portfolio(), date("2024-01-05"), 7);
    assertThat(s.analysis().dailyVolatility().value()).isNull();
    assertThat(s.analysis().var95().usd()).isNull();
    for (String query :
        List.of("date=2026-01-01", "date=2021-12-31", "lookbackDays=1462", "lookbackDays=1"))
      mvc.perform(
              get("/api/portfolios/" + f.portfolio() + "/risk/summary?" + query)
                  .with(user(f.email())))
          .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            get("/api/portfolios/" + f.portfolio() + "/risk/summary?date=invalid")
                .with(user(f.email())))
        .andExpect(status().isBadRequest());
    assertThatThrownBy(
            () ->
                risk.stress(
                    f.user(),
                    f.portfolio(),
                    new RiskService.StressRequest(
                        date("2024-01-05"),
                        Collections.nCopies(9, new Scenario("Too many", d("0"), Map.of())))))
        .isInstanceOf(DomainException.class);
  }
}
