package com.aegis;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aegis.auth.*;
import com.aegis.operations.*;
import com.aegis.portfolio.*;
import com.aegis.trading.*;
import com.aegis.web.RequestBodyLimit;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.system.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "aegis.auth.login-client-limit=3",
      "aegis.auth.registration-client-limit=3",
      "server.servlet.session.cookie.secure=true"
    })
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@Import(EngineeringSecurityTest.FailureConfiguration.class)
class EngineeringSecurityTest {
  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @DynamicPropertySource
  static void operator(DynamicPropertyRegistry r) {
    r.add(
        "aegis.operations.password-hash",
        () -> new BCryptPasswordEncoder(4).encode("operator-test-password"));
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuthService auth;
  @Autowired AttemptLimiter attempts;
  @Autowired UserRepository users;
  @Autowired PortfolioService portfolios;
  @Autowired TradingService trading;
  @Autowired ReconciliationService reconciliation;
  @Autowired MeterRegistry metrics;
  @Autowired ObjectMapper json;

  @BeforeEach
  void clearAttempts() {
    jdbc.update("delete from auth_attempt_window");
  }

  record Fixture(UUID user, String email, UUID portfolio) {}

  Fixture fixture() {
    String email = UUID.randomUUID() + "@example.test";
    var u =
        users.saveAndFlush(
            new AppUser(UUID.randomUUID(), email, "test-not-for-login", Instant.now()));
    return new Fixture(
        u.getId(),
        email,
        portfolios.create(u.getId(), "Engineering fixture", new BigDecimal("10000")).id());
  }

  TradingService.Request buy() {
    return new TradingService.Request(
        "ALTA", TradingService.Side.BUY, BigDecimal.ONE, LocalDate.of(2024, 1, 2));
  }

  double tradeCount(String outcome) {
    var c = metrics.find("aegis.trades").tag("outcome", outcome).tag("side", "BUY").counter();
    return c == null ? 0 : c.count();
  }

  @Test
  void accountThrottleDoesNotRevealExistenceAndExpiresAcrossNewLimiterInstances() {
    Instant now = Instant.parse("2026-10-17T00:00:00Z");
    var first = new AttemptLimiter(jdbc, Clock.fixed(now, ZoneOffset.UTC));
    first.acquire("test-account", "not-a-user@example.test", 2, Duration.ofMinutes(15));
    first.acquire("test-account", "not-a-user@example.test", 2, Duration.ofMinutes(15));
    var restarted = new AttemptLimiter(jdbc, Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC));
    assertThatThrownBy(
            () ->
                restarted.acquire(
                    "test-account", "not-a-user@example.test", 2, Duration.ofMinutes(15)))
        .isInstanceOf(AttemptLimiter.Limited.class);
    assertThat(jdbc.queryForObject("select bucket_key from auth_attempt_window", String.class))
        .matches("[a-f0-9]{64}");
    new AttemptLimiter(jdbc, Clock.fixed(now.plusSeconds(901), ZoneOffset.UTC))
        .acquire("test-account", "not-a-user@example.test", 2, Duration.ofMinutes(15));
  }

  @Test
  void concurrentAdmissionIsAtomic() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Boolean>> calls = new ArrayList<>();
      for (int i = 0; i < 8; i++)
        calls.add(
            () -> {
              try {
                attempts.acquire("concurrent", "same", 3, Duration.ofMinutes(5));
                return true;
              } catch (AttemptLimiter.Limited e) {
                return false;
              }
            });
      int allowed = 0;
      for (var f : pool.invokeAll(calls)) if (f.get()) allowed++;
      assertThat(allowed).isEqualTo(3);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void successfulAuthenticationResetsOnlyAccountWindow() {
    var identity = auth.register(UUID.randomUUID() + "@example.test", "valid-test-password");
    for (int i = 0; i < 4; i++)
      assertThatThrownBy(() -> auth.authenticate(identity.email(), "incorrect-password"))
          .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    assertThat(auth.authenticate(identity.email(), "valid-test-password").isAuthenticated())
        .isTrue();
    assertThat(jdbc.queryForObject("select count(*) from auth_attempt_window", Long.class))
        .isZero();
    for (int i = 0; i < 5; i++)
      assertThatThrownBy(() -> auth.authenticate("missing@example.test", "incorrect-password"))
          .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    assertThatThrownBy(() -> auth.authenticate("missing@example.test", "incorrect-password"))
        .isInstanceOf(AttemptLimiter.Limited.class);
  }

  @Test
  void loginRateLimitIgnoresSpoofedForwardedAddresses() throws Exception {
    String body = "{\"email\":\"missing@example.test\",\"password\":\"incorrect-password\"}";
    for (int i = 0; i < 3; i++)
      mvc.perform(
              post("/api/auth/login")
                  .with(csrf())
                  .header("X-Forwarded-For", "192.0.2." + i)
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .header("X-Forwarded-For", "192.0.2.99")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"));
  }

  @Test
  void registrationAbuseIsLimitedBeforePasswordWork() throws Exception {
    for (int i = 0; i < 3; i++)
      mvc.perform(
              post("/api/auth/register").with(csrf()).contentType("application/json").content("{}"))
          .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/auth/register").with(csrf()).contentType("application/json").content("{}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  void requestIdsSafeErrorsAndNoSecretLogging(CapturedOutput output) throws Exception {
    String sentinel = "do-not-log-password-cookie-secret";
    var response =
        mvc.perform(
                get("/api/test/failure?password=" + sentinel)
                    .with(user("test@example.test"))
                    .header("X-Request-ID", sentinel)
                    .header("Cookie", "sensitive=" + sentinel))
            .andExpect(status().isInternalServerError())
            .andExpect(
                header()
                    .string("X-Request-ID", org.hamcrest.Matchers.matchesPattern("[a-f0-9-]{36}")))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andReturn()
            .getResponse();
    assertThat(json.readTree(response.getContentAsString()).get("requestId").asString())
        .isEqualTo(response.getHeader("X-Request-ID"));
    assertThat(response.getContentAsString()).doesNotContain(sentinel);
    assertThat(output.getAll()).doesNotContain(sentinel);
    assertThat(
            metrics
                .find("aegis.requests")
                .tag("route", "/api/test/failure")
                .tag("outcome", "infrastructure_failure")
                .timer()
                .count())
        .isPositive();
    assertThat(org.slf4j.MDC.get("requestId")).isNull();
  }

  @Test
  void knownLengthAndStreamingBodiesAreCappedBeforeParsing() throws Exception {
    String body = "x".repeat(RequestBodyLimit.MAX_BYTES + 1);
    mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
        .andExpect(status().isPayloadTooLarge());
    MockHttpServletRequest req =
        new MockHttpServletRequest() {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    req.setMethod("POST");
    req.setRequestURI("/api/auth/login");
    req.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var res = new MockHttpServletResponse();
    new RequestBodyLimit(json)
        .doFilter(
            req,
            res,
            (a, b) -> {
              throw new AssertionError("Oversized body reached application");
            });
    assertThat(res.getStatus()).isEqualTo(413);
  }

  @Test
  void parserDepthAndStringLimitsReturnSafeValidationFailures() throws Exception {
    var f = fixture();
    for (String body :
        List.of(
            "{\"name\":\"" + "x".repeat(9000) + "\",\"initialCash\":1}",
            "[".repeat(25) + "0" + "]".repeat(25)))
      mvc.perform(
              post("/api/portfolios")
                  .with(user(f.email()))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isBadRequest());
  }

  @Test
  void operationsMetricsRequireSeparateCredentialsAndHealthIsMinimal() throws Exception {
    mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/metrics").with(user("normal@example.test")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/actuator/metrics").with(httpBasic("operator", "operator-test-password")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.names").isArray());
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
    mvc.perform(get("/actuator/env").with(httpBasic("operator", "operator-test-password")))
        .andExpect(status().isNotFound());
  }

  @Test
  void csrfCookiesHeadersAndCrossOriginPolicyRemainStrict() throws Exception {
    var res =
        mvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andReturn()
            .getResponse();
    assertThat(res.getCookie("XSRF-TOKEN").getSecure()).isTrue();
    assertThat(res.getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Strict");
    mvc.perform(
            options("/api/portfolios")
                .header("Origin", "https://hostile.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    mvc.perform(
            post("/api/portfolios")
                .with(user("normal@example.test"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void recoveryAndReconciliationAreOwnerScopedAndCannotAlterForeignHistory() throws Exception {
    var a = fixture();
    var b = fixture();
    var order = trading.execute(a.user(), a.portfolio(), "owner-key", buy());
    for (String suffix : List.of("/orders/by-key/owner-key", "/reconciliation")) {
      String url = "/api/portfolios/" + a.portfolio() + suffix;
      mvc.perform(get(url).with(user(a.email()))).andExpect(status().isOk());
      mvc.perform(get(url).with(user(b.email()))).andExpect(status().isNotFound());
      mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }
    var before = portfolios.detail(a.user(), a.portfolio());
    mvc.perform(
            post("/api/portfolios/" + a.portfolio() + "/orders")
                .with(user(b.email()))
                .with(csrf())
                .header("Idempotency-Key", "foreign-key")
                .contentType("application/json")
                .content(json.writeValueAsString(buy())))
        .andExpect(status().isNotFound());
    assertThat(portfolios.detail(a.user(), a.portfolio())).isEqualTo(before);
    assertThat(reconciliation.reconcile(a.user(), a.portfolio()).status()).isEqualTo("CONSISTENT");
  }

  @Test
  void reconciliationDetectsMutableBookCorruptionWithoutRepair() {
    var f = fixture();
    trading.execute(f.user(), f.portfolio(), "reconcile-key", buy());
    jdbc.update("update portfolio set cash=cash+1 where id=?", f.portfolio());
    assertThat(reconciliation.reconcile(f.user(), f.portfolio()).status())
        .isEqualTo("INCONSISTENT");
    assertThat(
            jdbc.queryForObject(
                "select cash from portfolio where id=?", BigDecimal.class, f.portfolio()))
        .isEqualByComparingTo(portfolios.detail(f.user(), f.portfolio()).portfolio().cash());
  }

  @Test
  void transactionMetricsDistinguishCommitReplayRejectionAndRollback() {
    var f = fixture();
    double committed = tradeCount("committed"),
        replayed = tradeCount("replayed"),
        rejected = tradeCount("rejected"),
        rolledBack = tradeCount("rolled_back");
    trading.execute(f.user(), f.portfolio(), "metric-key", buy());
    trading.execute(f.user(), f.portfolio(), "metric-key", buy());
    assertThatThrownBy(
            () ->
                trading.execute(
                    f.user(),
                    f.portfolio(),
                    "metric-bad",
                    new TradingService.Request(
                        "ALTA",
                        TradingService.Side.BUY,
                        new BigDecimal("1000000"),
                        buy().priceDate())))
        .isInstanceOf(com.aegis.shared.DomainException.class);
    jdbc.execute(
        "create function fail_engineering_audit() returns trigger language plpgsql as $$ begin"
            + " raise exception 'injected'; end $$");
    jdbc.execute(
        "create trigger fail_engineering_audit before insert on audit_event for each row execute"
            + " function fail_engineering_audit()");
    try {
      assertThatThrownBy(() -> trading.execute(f.user(), f.portfolio(), "metric-fail", buy()))
          .isInstanceOf(Exception.class);
    } finally {
      jdbc.execute("drop trigger fail_engineering_audit on audit_event");
      jdbc.execute("drop function fail_engineering_audit()");
    }
    assertThat(tradeCount("committed") - committed).isEqualTo(1);
    assertThat(tradeCount("replayed") - replayed).isEqualTo(1);
    assertThat(tradeCount("rejected") - rejected).isEqualTo(1);
    assertThat(tradeCount("rolled_back") - rolledBack).isEqualTo(1);
    assertThat(reconciliation.reconcile(f.user(), f.portfolio()).status()).isEqualTo("CONSISTENT");
  }

  @TestConfiguration
  static class FailureConfiguration {
    @Bean
    FailureController failureController() {
      return new FailureController();
    }
  }

  @RestController
  static class FailureController {
    @GetMapping("/api/test/failure")
    String fail() {
      throw new IllegalStateException("do-not-log-password-cookie-secret");
    }
  }
}
