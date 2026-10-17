package com.aegis;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aegis.analytics.*;
import com.aegis.auth.*;
import com.aegis.portfolio.*;
import com.aegis.shared.*;
import com.aegis.trading.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AnalyticsIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired PortfolioService portfolios;
  @Autowired TradingService trading;
  @Autowired AnalyticsService analytics;
  @Autowired MockMvc mvc;

  record Fixture(UUID user, String email, UUID portfolio) {}

  static BigDecimal d(String x) {
    return new BigDecimal(x);
  }

  static LocalDate day(int x) {
    return LocalDate.of(2024, 1, x);
  }

  @BeforeEach
  void prices() {
    for (String symbol : List.of("PVAA", "PVBB")) {
      jdbc.update(
          "insert into asset values(?,?,'Testing','USD','phase2-test-only') on conflict do nothing",
          symbol,
          symbol + " (fictional test fixture)");
      for (int i = 2; i <= 5; i++) {
        String value =
            symbol.equals("PVAA")
                ? switch (i) {
                  case 2 -> "10";
                  case 3 -> "15";
                  case 4 -> "12";
                  default -> "13";
                }
                : "20";
        jdbc.update(
            "insert into historical_price(asset_symbol,price_date,close_price) values(?,?,?) on"
                + " conflict do nothing",
            symbol,
            day(i),
            d(value));
      }
      jdbc.update(
          "insert into historical_price(asset_symbol,price_date,close_price) values(?,?,14) on"
              + " conflict do nothing",
          symbol,
          day(8));
    }
  }

  Fixture fixture() {
    return fixture("1000");
  }

  Fixture fixture(String funding) {
    String email = UUID.randomUUID() + "@example.test";
    // Endpoint authorization tests use Spring Security's test principal; real login remains covered
    // in Phase 1.
    var u =
        users.saveAndFlush(
            new AppUser(
                UUID.randomUUID(), email, "not-used-for-login-in-this-fixture", Instant.now()));
    return new Fixture(
        u.getId(), email, portfolios.create(u.getId(), "Valuation fixture", d(funding)).id());
  }

  void trade(Fixture f, String symbol, TradingService.Side side, String quantity, int date) {
    trading.execute(
        f.user(),
        f.portfolio(),
        UUID.randomUUID().toString(),
        new TradingService.Request(symbol, side, d(quantity), day(date)));
  }

  Fixture scenario() {
    var f = fixture();
    trade(f, "PVAA", TradingService.Side.BUY, "10", 2);
    trade(f, "PVAA", TradingService.Side.SELL, "4", 3);
    return f;
  }

  void rejects(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void exactRequiredScenarioUsesStoredExecutionsPricesAndAllocations() throws Exception {
    var f = scenario();
    var s = analytics.valuation(f.user(), f.portfolio(), day(4));
    assertThat(s.cash()).isEqualByComparingTo("960");
    assertThat(s.marketValue()).isEqualByComparingTo("72");
    assertThat(s.totalValue()).isEqualByComparingTo("1032");
    assertThat(s.realizedPnl()).isEqualByComparingTo("20");
    assertThat(s.unrealizedPnl()).isEqualByComparingTo("12");
    assertThat(s.totalPnl()).isEqualByComparingTo("32");
    assertThat(s.returnPercent()).isEqualByComparingTo("3.20");
    assertThat(s.positions())
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.quantity()).isEqualByComparingTo("6");
              assertThat(p.costBasis()).isEqualByComparingTo("60");
              assertThat(p.valuationPrice()).isEqualByComparingTo("12");
            });
    mvc.perform(
            get("/api/portfolios/" + f.portfolio() + "/valuation?date=2024-01-04")
                .with(user(f.email())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalValue").value("1032.00"))
        .andExpect(jsonPath("$.returnPercent").value("3.2000"));
  }

  @Test
  void pastSnapshotsIgnoreFutureSalesAndReconcileOriginalBuyCost() {
    var f = scenario();
    var before = analytics.valuation(f.user(), f.portfolio(), day(1));
    assertThat(before.cash()).isEqualByComparingTo("1000");
    assertThat(before.positions()).isEmpty();
    var bought = analytics.valuation(f.user(), f.portfolio(), day(2));
    assertThat(bought.cash()).isEqualByComparingTo("900");
    assertThat(bought.positions().getFirst().quantity()).isEqualByComparingTo("10");
    assertThat(bought.positions().getFirst().costBasis()).isEqualByComparingTo("100");
    assertThat(bought.realizedPnl()).isZero();
    assertThat(portfolios.detail(f.user(), f.portfolio()).holdings().getFirst().quantity())
        .isEqualByComparingTo("6");
  }

  @Test
  void noTradesHaveFlatCapitalEvenOnWeekendsAndAtSimulationStart() {
    var f = fixture();
    var performance =
        analytics.performance(
            f.user(),
            f.portfolio(),
            SimulationCalendar.START,
            SimulationCalendar.START.plusDays(6));
    assertThat(performance.series().completeDays()).isEqualTo(7);
    assertThat(performance.series().missingDays()).isZero();
    assertThat(performance.series().points())
        .allSatisfy(
            p -> {
              assertThat(p.totalValue()).isEqualByComparingTo("1000");
              assertThat(p.totalPnl()).isZero();
            });
    assertThat(performance.allocation().items())
        .singleElement()
        .satisfies(i -> assertThat(i.weightPercent()).isEqualByComparingTo("100"));
  }

  @Test
  void multipleAssetsUseEachExactDatedClose() {
    var f = fixture();
    trade(f, "PVAA", TradingService.Side.BUY, "10", 2);
    trade(f, "PVBB", TradingService.Side.BUY, "2", 2);
    var s = analytics.valuation(f.user(), f.portfolio(), day(4));
    assertThat(s.cash()).isEqualByComparingTo("860");
    assertThat(s.marketValue()).isEqualByComparingTo("160");
    assertThat(s.totalValue()).isEqualByComparingTo("1020");
    assertThat(s.positions()).hasSize(2);
    var a = analytics.allocation(f.user(), f.portfolio(), day(4));
    assertThat(a.items()).hasSize(3);
    assertThat(a.items().getFirst().weightPercent()).isEqualByComparingTo("84.3137");
  }

  @Test
  void fullSaleLeavesRealizedProfitAndNeedsNoWeekendPrice() {
    var f = scenario();
    trade(f, "PVAA", TradingService.Side.SELL, "6", 4);
    var s = analytics.valuation(f.user(), f.portfolio(), day(6));
    assertThat(s.status()).isEqualTo(ValuationEngine.Status.COMPLETE);
    assertThat(s.cash()).isEqualByComparingTo("1032");
    assertThat(s.marketValue()).isZero();
    assertThat(s.realizedPnl()).isEqualByComparingTo("32");
    assertThat(s.positions().getFirst().costBasis()).isZero();
  }

  @Test
  void sameDayBuysAndSellsHaveStableEndOfDayTotals() {
    var f = fixture();
    trade(f, "PVAA", TradingService.Side.BUY, "10", 2);
    trade(f, "PVAA", TradingService.Side.BUY, "5", 2);
    trade(f, "PVAA", TradingService.Side.SELL, "12", 2);
    var s = analytics.valuation(f.user(), f.portfolio(), day(2));
    assertThat(s.cash()).isEqualByComparingTo("970");
    assertThat(s.positions().getFirst().quantity()).isEqualByComparingTo("3");
    assertThat(s.positions().getFirst().costBasis()).isEqualByComparingTo("30");
    assertThat(s).isEqualTo(analytics.valuation(f.user(), f.portfolio(), day(2)));
  }

  @Test
  void weekendAndWeekdayPriceGapsAreNotZeroOrForwardFilled() {
    var f = scenario();
    var series = analytics.series(f.user(), f.portfolio(), day(5), day(9));
    assertThat(series.completeDays()).isEqualTo(2);
    assertThat(series.missingDays()).isEqualTo(3);
    assertThat(series.points().get(1).totalValue()).isNull();
    assertThat(series.points().get(4).totalValue()).isNull();
    assertThat(series.points().get(4).missingSymbols()).containsExactly("PVAA");
    assertThat(analytics.allocation(f.user(), f.portfolio(), day(6)).status())
        .isEqualTo("MISSING_PRICES");
  }

  @Test
  void negativeReturnsAreCalculatedFromTheActualMark() {
    var f = fixture();
    trade(f, "PVAA", TradingService.Side.BUY, "10", 3);
    var s = analytics.valuation(f.user(), f.portfolio(), day(4));
    assertThat(s.totalPnl()).isEqualByComparingTo("-30");
    assertThat(s.returnPercent()).isEqualByComparingTo("-3");
  }

  @Test
  void performanceSeparatesRangeReturnFromInitialCapitalReturn() {
    var f = scenario();
    var p = analytics.performance(f.user(), f.portfolio(), day(3), day(4));
    assertThat(p.period().change()).isEqualByComparingTo("-18");
    assertThat(p.period().returnPercent()).isEqualByComparingTo("-1.7143");
    assertThat(p.summary().returnPercent()).isEqualByComparingTo("3.2");
  }

  @Test
  void datesBeforeStartAfterHorizonOrReversedAreRejected() {
    var f = fixture();
    rejects(
        () -> analytics.valuation(f.user(), f.portfolio(), SimulationCalendar.START.minusDays(1)),
        "BEFORE_SIMULATION_START");
    rejects(
        () -> analytics.valuation(f.user(), f.portfolio(), SimulationCalendar.END.plusDays(1)),
        "INVALID_DATE_RANGE");
    rejects(() -> analytics.series(f.user(), f.portfolio(), day(4), day(2)), "INVALID_DATE_RANGE");
    rejects(
        () ->
            analytics.series(
                f.user(), f.portfolio(), LocalDate.of(2000, 1, 1), SimulationCalendar.END),
        "INVALID_DATE_RANGE");
  }

  @Test
  void allNewRoutesEnforceOwnershipAndAuthentication() throws Exception {
    var f = scenario();
    var other = fixture();
    for (String endpoint :
        List.of(
            "valuation",
            "value-series?from=2024-01-02&to=2024-01-04",
            "performance?from=2024-01-02&to=2024-01-04",
            "holdings",
            "allocation")) {
      String path = "/api/portfolios/" + f.portfolio() + "/" + endpoint;
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      mvc.perform(get(path).with(user(other.email()))).andExpect(status().isNotFound());
      mvc.perform(get(path).with(user(f.email()))).andExpect(status().isOk());
    }
  }

  @Test
  void malformedAndMissingDateParametersHaveConsistentApiErrors() throws Exception {
    var f = fixture();
    for (String endpoint :
        List.of(
            "valuation?date=not-a-date",
            "performance?from=2024-01-01",
            "value-series?from=2024-01-01&to=invalid"))
      mvc.perform(get("/api/portfolios/" + f.portfolio() + "/" + endpoint).with(user(f.email())))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    mvc.perform(
            get("/api/portfolios/" + f.portfolio() + "/performance?from=2024-02-01&to=2024-01-01")
                .with(user(f.email())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
  }

  @Test
  void historicalQueryHasAHardExecutionLimitRatherThanSilentTruncation() {
    var f = fixture("1000000");
    jdbc.execute(
        "insert into"
            + " executed_order(id,portfolio_id,asset_symbol,side,quantity,execution_price,gross_amount,realized_pnl,price_date,executed_at,recorded_at,idempotency_key)"
            + " select md5('limit-order-'||s)::uuid,'"
            + f.portfolio()
            + "'::uuid,'PVAA','BUY',1,10,10,0,DATE '2024-01-02',now(),now(),'limit-'||s from"
            + " generate_series(1,10001) s");
    rejects(() -> analytics.valuation(f.user(), f.portfolio(), day(2)), "ANALYTICS_LIMIT");
  }

  @Test
  void incompleteEndpointsKeepPeriodChangeUnknown() {
    var f = scenario();
    var p = analytics.performance(f.user(), f.portfolio(), day(6), day(8));
    assertThat(p.period().status()).isEqualTo("MISSING_ENDPOINT_PRICE");
    assertThat(p.period().returnPercent()).isNull();
  }

  @Test
  void existingV1DataSurvivesMigrationWithSyntheticFundingBaseline() {
    String schema = "legacy_phase1";
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("1")
        .load()
        .migrate();
    String url =
        postgres.getJdbcUrl()
            + (postgres.getJdbcUrl().contains("?") ? "&" : "?")
            + "currentSchema="
            + schema;
    var legacy =
        new JdbcTemplate(
            new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
    UUID owner = UUID.randomUUID(), portfolio = UUID.randomUUID(), order = UUID.randomUUID();
    legacy.update(
        "insert into app_user values(?, 'legacy@example.test','fixture-only',TIMESTAMPTZ"
            + " '2026-10-01 10:00:00Z')",
        owner);
    legacy.update(
        "insert into portfolio values(?,?, 'Legacy','USD',900,DATE '2024-01-02',TIMESTAMPTZ"
            + " '2026-10-01 10:00:00Z')",
        portfolio,
        owner);
    legacy.update(
        "insert into asset values('OLD','Fictional legacy asset','Testing','USD','test')");
    legacy.update(
        "insert into historical_price(asset_symbol,price_date,close_price) values('OLD',DATE"
            + " '2024-01-02',10)");
    legacy.update(
        "insert into executed_order values(?,?,'OLD','BUY',10,10,100,0,DATE"
            + " '2024-01-02',TIMESTAMPTZ '2024-01-02 21:00:00Z',TIMESTAMPTZ '2026-10-01"
            + " 10:01:00Z','legacy-buy-key')",
        order,
        portfolio);
    legacy.update(
        "insert into"
            + " position_lot(id,portfolio_id,asset_symbol,buy_order_id,original_quantity,remaining_quantity,remaining_cost,acquired_at)"
            + " values(?,?,'OLD',?,10,10,100,TIMESTAMPTZ '2024-01-02 21:00:00Z')",
        UUID.randomUUID(),
        portfolio,
        order);
    legacy.update(
        "insert into cash_ledger values(?,?,null,'FUNDING',1000,1000,TIMESTAMPTZ '2026-10-01"
            + " 10:00:00Z')",
        UUID.randomUUID(),
        portfolio);
    legacy.update(
        "insert into cash_ledger values(?,?,?,'BUY',-100,900,TIMESTAMPTZ '2026-10-01 10:01:00Z')",
        UUID.randomUUID(),
        portfolio,
        order);
    String before =
        legacy.queryForObject("select row_to_json(o)::text from executed_order o", String.class);
    String ledger =
        legacy.queryForObject(
            "select json_agg(l order by recorded_at)::text from cash_ledger l", String.class);
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .load()
        .migrate();
    assertThat(
            legacy.queryForObject("select simulation_start_date from portfolio", LocalDate.class))
        .isEqualTo(SimulationCalendar.START);
    assertThat(
            legacy.queryForObject(
                "select row_to_json(o)::text from executed_order o", String.class))
        .isEqualTo(before);
    assertThat(
            legacy.queryForObject(
                "select json_agg(l order by recorded_at)::text from cash_ledger l", String.class))
        .isEqualTo(ledger);
    assertThat(legacy.queryForObject("select cash from portfolio", BigDecimal.class))
        .isEqualByComparingTo("900");
    assertThat(
            legacy
                .queryForObject("select created_at from portfolio", java.sql.Timestamp.class)
                .toInstant())
        .isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
    var repository = new AnalyticsRepository(legacy);
    var events = repository.events(portfolio, day(2));
    var value =
        ValuationEngine.timeline(
                repository.funding(portfolio),
                day(2),
                day(2),
                events,
                repository.prices(Set.of("OLD"), day(2), day(2)))
            .last();
    assertThat(value.totalValue()).isEqualByComparingTo("1000");
    assertThat(value.returnPercent()).isZero();
  }

  @Test
  void zeroValueAllocationAndRangeDenominatorAreExplicitlyUndefined() {
    var f = fixture("0.01");
    jdbc.update(
        "insert into asset values('ZEROT','Fictional zero-value"
            + " fixture','Testing','USD','test-only')");
    jdbc.update(
        "insert into historical_price(asset_symbol,price_date,close_price) values('ZEROT',DATE"
            + " '2024-01-02',10000),('ZEROT',DATE '2024-01-03',1),('ZEROT',DATE"
            + " '2024-01-04',20000)");
    trade(f, "ZEROT", TradingService.Side.BUY, "0.000001", 2);
    var allocation = analytics.allocation(f.user(), f.portfolio(), day(3));
    assertThat(allocation.status()).isEqualTo("ZERO_TOTAL_VALUE");
    assertThat(allocation.items()).allSatisfy(item -> assertThat(item.weightPercent()).isNull());
    var performance = analytics.performance(f.user(), f.portfolio(), day(3), day(4));
    assertThat(performance.period().status()).isEqualTo("ZERO_START_VALUE");
    assertThat(performance.period().returnPercent()).isNull();
    assertThat(performance.period().change()).isEqualByComparingTo("0.02");
    assertThat(performance.series().points().getFirst().returnPercent())
        .isEqualByComparingTo("-100");
  }
}
