package com.aegis;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aegis.auth.*;
import com.aegis.market.*;
import com.aegis.portfolio.*;
import com.aegis.shared.DomainException;
import com.aegis.trading.*;
import tools.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlatformIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired PortfolioService portfolios;
  @Autowired TradingService trading;
  @Autowired OrderRepository orders;
  @Autowired HistoryRepository history;
  @Autowired MarketService market;
  @Autowired SyntheticDataLoader loader;

  record Account(UUID id, String email, MockHttpSession session) {}

  record Fixture(Account account, PortfolioService.View portfolio) {}

  @BeforeEach
  void fixturePrices() {
    jdbc.update(
        "insert into asset values('TST','Test fixture (fictional)','Testing','USD','test-only') on"
            + " conflict do nothing");
    for (int day = 2; day <= 4; day++)
      jdbc.update(
          "insert into historical_price(asset_symbol,price_date,close_price) values('TST',?,?) on"
              + " conflict do nothing",
          LocalDate.of(2024, 1, day),
          BigDecimal.valueOf((day - 1) * 10));
  }

  Account account() throws Exception {
    String email = UUID.randomUUID() + "@example.test";
    var result =
        mvc.perform(
                post("/api/auth/register")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of("email", email, "password", "correct-horse-battery"))))
            .andExpect(status().isCreated())
            .andReturn();
    return new Account(
        users.findByEmail(email).orElseThrow().getId(),
        email,
        (MockHttpSession) result.getRequest().getSession(false));
  }

  Fixture fixture(String funding) throws Exception {
    var account = account();
    return new Fixture(
        account, portfolios.create(account.id(), "Integration portfolio", new BigDecimal(funding)));
  }

  TradingService.Request request(TradingService.Side side, String quantity, int day) {
    return new TradingService.Request(
        "TST", side, new BigDecimal(quantity), LocalDate.of(2024, 1, day));
  }

  TradingService.Execution execute(
      Fixture f, TradingService.Side side, String quantity, int day, String key) {
    return trading.execute(f.account.id(), f.portfolio.id(), key, request(side, quantity, day));
  }

  void rejected(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  BigDecimal balance(Fixture f) {
    return portfolios.detail(f.account.id(), f.portfolio.id()).portfolio().cash();
  }

  @Test
  void fundingIsAtomicAndVisibleInLedgerAndAudit() throws Exception {
    var f = fixture("1000.00");
    assertThat(history.ledger(f.portfolio.id(), 0, 10))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.kind()).isEqualTo("FUNDING");
              assertThat(e.amount()).isEqualByComparingTo("1000.00");
            });
    assertThat(history.audit(f.portfolio.id(), 0, 10))
        .singleElement()
        .extracting(HistoryRepository.Audit::eventType)
        .isEqualTo("PORTFOLIO_FUNDED");
    mvc.perform(get("/api/portfolios").session(f.account.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].cash").value("1000.00"));
  }

  @Test
  void buysAndSellsUseFifoAndReconcileLedger() throws Exception {
    var f = fixture("1000.00");
    execute(f, TradingService.Side.BUY, "10", 2, "fifo-buy-first");
    execute(f, TradingService.Side.BUY, "5", 3, "fifo-buy-second");
    var sale = execute(f, TradingService.Side.SELL, "12", 4, "fifo-sell-first");
    assertThat(sale.realizedPnl()).isEqualByComparingTo("220.00");
    assertThat(sale.grossAmount()).isEqualByComparingTo("360.00");
    assertThat(sale.executionPrice()).isEqualByComparingTo("30.0000");
    assertThat(sale.executedAt()).isEqualTo(Instant.parse("2024-01-04T21:00:00Z"));
    var detail = portfolios.detail(f.account.id(), f.portfolio.id());
    assertThat(detail.portfolio().cash()).isEqualByComparingTo("1160.00");
    assertThat(detail.holdings())
        .singleElement()
        .satisfies(
            h -> {
              assertThat(h.quantity()).isEqualByComparingTo("3");
              assertThat(h.costBasis()).isEqualByComparingTo("60.00");
            });
    assertThat(
            jdbc.queryForObject(
                "select sum(amount) from cash_ledger where portfolio_id=?",
                BigDecimal.class,
                f.portfolio.id()))
        .isEqualByComparingTo("1160.00");
    assertThat(
            jdbc.queryForObject(
                "select sum(cost) from sell_allocation where sell_order_id=?",
                BigDecimal.class,
                sale.id()))
        .isEqualByComparingTo("140.00");
    assertThat(history.auditCount(f.portfolio.id())).isEqualTo(4);
  }

  @Test
  void rejectionLeavesCashLotsAndHistoryUnchanged() throws Exception {
    var f = fixture("100.00");
    rejected(
        () -> execute(f, TradingService.Side.BUY, "11", 2, "insufficient-buy"),
        "INSUFFICIENT_CASH");
    rejected(
        () -> execute(f, TradingService.Side.SELL, "1", 2, "overselling-key"),
        "INSUFFICIENT_HOLDINGS");
    assertThat(balance(f)).isEqualByComparingTo("100.00");
    assertThat(history.ledgerCount(f.portfolio.id())).isEqualTo(1);
    assertThat(orders.findByPortfolioIdAndIdempotencyKey(f.portfolio.id(), "insufficient-buy"))
        .isEmpty();
  }

  @Test
  void duplicateOrderReturnsOriginalAndDifferentPayloadConflicts() throws Exception {
    var f = fixture("1000.00");
    var a = execute(f, TradingService.Side.BUY, "2", 2, "duplicate-key");
    var b = execute(f, TradingService.Side.BUY, "2.000000", 2, "duplicate-key");
    assertThat(a.id()).isEqualTo(b.id());
    assertThat(balance(f)).isEqualByComparingTo("980.00");
    assertThat(history.ledgerCount(f.portfolio.id())).isEqualTo(2);
    rejected(
        () -> execute(f, TradingService.Side.BUY, "3", 2, "duplicate-key"), "IDEMPOTENCY_CONFLICT");
  }

  @Test
  void datesCannotTravelBackwardsButIdempotentReplayCan() throws Exception {
    var f = fixture("1000");
    var old = execute(f, TradingService.Side.BUY, "1", 2, "earlier-order");
    execute(f, TradingService.Side.BUY, "1", 3, "later-order");
    rejected(
        () -> execute(f, TradingService.Side.BUY, "1", 2, "backdated-order"), "DATE_OUT_OF_ORDER");
    assertThat(execute(f, TradingService.Side.BUY, "1", 2, "earlier-order").id())
        .isEqualTo(old.id());
  }

  @Test
  void missingPriceIsNotAZeroPrice() throws Exception {
    var f = fixture("1000");
    var req =
        new TradingService.Request(
            "ALTA", TradingService.Side.BUY, new BigDecimal("1"), LocalDate.of(2024, 1, 6));
    rejected(
        () -> trading.execute(f.account.id(), f.portfolio.id(), "weekend-gap-key", req),
        "PRICE_UNAVAILABLE");
    assertThat(market.quotes(LocalDate.of(2024, 1, 6)))
        .allSatisfy(q -> assertThat(q.closePrice()).isNull());
    assertThat(balance(f)).isEqualByComparingTo("1000");
  }

  @Test
  void concurrentBuysCannotDoubleSpend() throws Exception {
    var f = fixture("1000");
    var outcomes =
        race(
            () -> execute(f, TradingService.Side.BUY, "80", 2, "concurrent-one"),
            () -> execute(f, TradingService.Side.BUY, "80", 2, "concurrent-two"));
    assertThat(outcomes.stream().filter(TradingService.Execution.class::isInstance)).hasSize(1);
    assertThat(
            outcomes.stream()
                .filter(DomainException.class::isInstance)
                .map(DomainException.class::cast))
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo("INSUFFICIENT_CASH"));
    assertThat(balance(f)).isEqualByComparingTo("200.00");
    assertThat(portfolios.detail(f.account.id(), f.portfolio.id()).holdings())
        .singleElement()
        .satisfies(h -> assertThat(h.quantity()).isEqualByComparingTo("80"));
  }

  @Test
  void concurrentDuplicateExecutesExactlyOnce() throws Exception {
    var f = fixture("1000");
    var outcomes =
        race(
            () -> execute(f, TradingService.Side.BUY, "2", 2, "same-concurrent-key"),
            () -> execute(f, TradingService.Side.BUY, "2", 2, "same-concurrent-key"));
    assertThat(outcomes)
        .allSatisfy(o -> assertThat(o).isInstanceOf(TradingService.Execution.class));
    assertThat(((TradingService.Execution) outcomes.get(0)).id())
        .isEqualTo(((TradingService.Execution) outcomes.get(1)).id());
    assertThat(history.ledgerCount(f.portfolio.id())).isEqualTo(2);
  }

  @Test
  void concurrentSellsCannotOversell() throws Exception {
    var f = fixture("1000");
    execute(f, TradingService.Side.BUY, "10", 2, "initial-buy-key");
    var outcomes =
        race(
            () -> execute(f, TradingService.Side.SELL, "8", 3, "sell-concurrent-one"),
            () -> execute(f, TradingService.Side.SELL, "8", 3, "sell-concurrent-two"));
    assertThat(outcomes.stream().filter(TradingService.Execution.class::isInstance)).hasSize(1);
    assertThat(
            outcomes.stream()
                .filter(DomainException.class::isInstance)
                .map(DomainException.class::cast))
        .singleElement()
        .satisfies(e -> assertThat(e.code()).isEqualTo("INSUFFICIENT_HOLDINGS"));
    assertThat(balance(f)).isEqualByComparingTo("1060.00");
  }

  List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
    CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<Future<Object>>();
      for (var task : List.of(first, second))
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("Race start timeout");
                  try {
                    return task.call();
                  } catch (DomainException e) {
                    return e;
                  }
                }));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(
          futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
    }
  }

  @Test
  void ownershipIsEnforcedAcrossAllPortfolioRoutes() throws Exception {
    var f = fixture("1000");
    var other = account();
    for (String suffix : List.of("", "/orders", "/ledger", "/audit")) {
      mvc.perform(get("/api/portfolios/" + f.portfolio.id() + suffix).session(other.session()))
          .andExpect(status().isNotFound());
    }
    mvc.perform(
            post("/api/portfolios/" + f.portfolio.id() + "/orders")
                .session(other.session())
                .with(csrf())
                .header("Idempotency-Key", "foreign-order-key")
                .contentType("application/json")
                .content(json.writeValueAsString(request(TradingService.Side.BUY, "1", 2))))
        .andExpect(status().isNotFound());
    assertThat(balance(f)).isEqualByComparingTo("1000");
  }

  @Test
  void anonymousAndMissingCsrfAreRejected() throws Exception {
    mvc.perform(get("/api/portfolios"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void invalidInputsHaveConsistentErrors() throws Exception {
    var f = fixture("1000");
    for (String quantity : List.of("0", "-1", "0.0000001", "1000001")) {
      mvc.perform(
              post("/api/portfolios/" + f.portfolio.id() + "/orders")
                  .session(f.account.session())
                  .with(csrf())
                  .header("Idempotency-Key", "invalid-order-key")
                  .contentType("application/json")
                  .content(
                      "{\"symbol\":\"TST\",\"side\":\"BUY\",\"quantity\":\""
                          + quantity
                          + "\",\"priceDate\":\"2024-01-02\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
    mvc.perform(get("/api/portfolios?page=-1").session(f.account.session()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/portfolios")
                .session(f.account.session())
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\" \",\"initialCash\":\"-1\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/portfolios/not-a-uuid").session(f.account.session()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void loginLogoutAndPasswordHashingWork() throws Exception {
    var a = account();
    assertThat(users.findByEmail(a.email()).orElseThrow().getPasswordHash())
        .startsWith("$2a$")
        .isNotEqualTo("correct-horse-battery");
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("email", a.email(), "password", "wrong-password-value"))))
        .andExpect(status().isUnauthorized());
    var login =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                a.email().toUpperCase(Locale.ROOT),
                                "password",
                                "correct-horse-battery"))))
            .andExpect(status().isOk())
            .andReturn();
    var session = (MockHttpSession) login.getRequest().getSession(false);
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
    mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
        .andExpect(status().isNoContent());
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  void historyCannotBeRewritten() throws Exception {
    var f = fixture("1000");
    var order = execute(f, TradingService.Side.BUY, "1", 2, "immutable-buy-key");
    for (String sql :
        List.of(
            "update cash_ledger set amount=0 where portfolio_id=?",
            "delete from audit_event where portfolio_id=?",
            "delete from executed_order where portfolio_id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, f.portfolio.id()))
          .isInstanceOf(DataAccessException.class);
    assertThat(orders.findById(order.id())).isPresent();
  }

  @Test
  void syntheticSeedCanRunAgainWithoutChangingPrices() {
    var before = market.exactClose("ALTA", LocalDate.of(2024, 1, 2));
    loader.run(null);
    assertThat(market.exactClose("ALTA", LocalDate.of(2024, 1, 2))).isEqualByComparingTo(before);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from historical_price where asset_symbol in"
                    + " ('ALTA','BRIM','COVE')",
                Long.class))
        .isEqualTo(3129);
  }

  @Test
  void paginationIsBoundedAndDeterministic() throws Exception {
    var f = fixture("1000");
    execute(f, TradingService.Side.BUY, "1", 2, "page-buy-one");
    execute(f, TradingService.Side.BUY, "2", 3, "page-buy-two");
    mvc.perform(
            get("/api/portfolios/" + f.portfolio.id() + "/orders?size=1&page=0")
                .session(f.account.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items[0].quantity").value("2.000000"));
    mvc.perform(get("/api/portfolios?size=101").session(f.account.session()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void failureAfterAccountingWritesRollsBackEverything() throws Exception {
    var f = fixture("1000");
    // Inject a DB failure at the final audit insert, after the execution has been flushed.
    String function = "fail_audit_" + f.portfolio.id().toString().replace("-", "");
    jdbc.execute(
        "create function "
            + function
            + "() returns trigger language plpgsql as $$ begin raise exception 'injected audit"
            + " failure'; end $$");
    jdbc.execute(
        "create trigger "
            + function
            + " before insert on audit_event for each row when (new.portfolio_id = '"
            + f.portfolio.id()
            + "'::uuid) execute function "
            + function
            + "()");
    try {
      assertThatThrownBy(() -> execute(f, TradingService.Side.BUY, "2", 2, "rolled-back-key"))
          .isInstanceOf(DataAccessException.class);
      assertThat(balance(f)).isEqualByComparingTo("1000");
      assertThat(portfolios.detail(f.account.id(), f.portfolio.id()).holdings()).isEmpty();
      assertThat(orders.findByPortfolioIdAndIdempotencyKey(f.portfolio.id(), "rolled-back-key"))
          .isEmpty();
      assertThat(history.ledgerCount(f.portfolio.id())).isEqualTo(1);
      assertThat(history.auditCount(f.portfolio.id())).isEqualTo(1);
    } finally {
      jdbc.execute("drop trigger " + function + " on audit_event");
      jdbc.execute("drop function " + function + "()");
    }
    execute(f, TradingService.Side.BUY, "2", 2, "rolled-back-key");
    assertThat(balance(f)).isEqualByComparingTo("980");
  }

  @Test
  void missingKeysUnknownFieldsAndUnsupportedMethodsAreSafeErrors() throws Exception {
    var f = fixture("1000");
    mvc.perform(
            post("/api/portfolios/" + f.portfolio.id() + "/orders")
                .session(f.account.session())
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(request(TradingService.Side.BUY, "1", 2))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    mvc.perform(
            post("/api/portfolios")
                .session(f.account.session())
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Test\",\"initialCash\":\"1.001\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/portfolios")
                .session(f.account.session())
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Test\",\"initialCash\":\"100\",\"ownerId\":\"injected\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            delete("/api/portfolios/" + f.portfolio.id()).session(f.account.session()).with(csrf()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void authenticatedOpenApiDescribesDecimalStrings() throws Exception {
    var a = account();
    mvc.perform(get("/v3/api-docs").session(a.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("AEGIS simulated investing API"))
        .andExpect(
            jsonPath("$.components.schemas.Create.properties.initialCash.type").value("string"))
        .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty());
  }
}
