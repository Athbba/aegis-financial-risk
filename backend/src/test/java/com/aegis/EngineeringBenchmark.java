package com.aegis;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.aegis.auth.*;
import com.aegis.portfolio.*;
import com.aegis.trading.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/** Explicit opt-in: mvn -Dtest=EngineeringBenchmark test. Never touches the Compose database. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EngineeringBenchmark {
  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired UserRepository users;
  @Autowired PortfolioService portfolios;
  @Autowired TradingService trading;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @FunctionalInterface
  interface Request {
    void run() throws Exception;
  }

  Map<String, Object> measure(String name, int samples, Request request) throws Exception {
    for (int i = 0; i < 10; i++) request.run();
    double[] ms = new double[samples];
    for (int i = 0; i < samples; i++) {
      long start = System.nanoTime();
      request.run();
      ms[i] = (System.nanoTime() - start) / 1e6;
    }
    Arrays.sort(ms);
    return Map.of(
        "operation",
        name,
        "samples",
        samples,
        "warmup",
        10,
        "minMs",
        ms[0],
        "p50Ms",
        ms[(int) Math.ceil(samples * .5) - 1],
        "p95Ms",
        ms[(int) Math.ceil(samples * .95) - 1],
        "maxMs",
        ms[samples - 1]);
  }

  @Test
  void benchmark() throws Exception {
    String email = UUID.randomUUID() + "@example.test";
    var u =
        users.saveAndFlush(
            new AppUser(UUID.randomUUID(), email, "benchmark-not-for-login", Instant.now()));
    var p =
        portfolios.create(u.getId(), "Disposable synthetic benchmark", new BigDecimal("1000000"));
    for (int i = 0; i < 300; i++)
      trading.execute(
          u.getId(),
          p.id(),
          "seed-" + i,
          new TradingService.Request(
              List.of("ALTA", "BRIM", "COVE").get(i % 3),
              TradingService.Side.BUY,
              BigDecimal.ONE,
              LocalDate.of(2024, 1, 2)));
    String base = "/api/portfolios/" + p.id();
    List<Map<String, Object>> results = new ArrayList<>();
    results.add(
        measure(
            "create portfolio",
            40,
            () ->
                assertThat(
                        mvc.perform(
                                post("/api/portfolios")
                                    .with(user(email))
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content("{\"name\":\"Benchmark\",\"initialCash\":1000}"))
                            .andReturn()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(201)));
    results.add(
        measure(
            "trade committed buy",
            100,
            () ->
                assertThat(
                        mvc.perform(
                                post(base + "/orders")
                                    .with(user(email))
                                    .with(csrf())
                                    .header("Idempotency-Key", UUID.randomUUID().toString())
                                    .contentType("application/json")
                                    .content(
                                        "{\"symbol\":\"ALTA\",\"side\":\"BUY\",\"quantity\":1,\"priceDate\":\"2024-01-02\"}"))
                            .andReturn()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(200)));
    results.add(
        measure(
            "trade committed FIFO sell",
            100,
            () ->
                assertThat(
                        mvc.perform(
                                post(base + "/orders")
                                    .with(user(email))
                                    .with(csrf())
                                    .header("Idempotency-Key", UUID.randomUUID().toString())
                                    .contentType("application/json")
                                    .content(
                                        "{\"symbol\":\"ALTA\",\"side\":\"SELL\",\"quantity\":0.1,\"priceDate\":\"2024-01-02\"}"))
                            .andReturn()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(200)));
    results.add(
        measure(
            "historical valuation",
            100,
            () ->
                assertThat(
                        mvc.perform(get(base + "/valuation?date=2025-12-31").with(user(email)))
                            .andReturn()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(200)));
    results.add(
        measure(
            "historical performance 730 days",
            50,
            () ->
                assertThat(
                        mvc.perform(
                                get(base + "/performance?from=2024-01-02&to=2025-12-31")
                                    .with(user(email)))
                            .andReturn()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(200)));
    results.add(
        measure(
            "risk summary 730 days",
            50,
            () ->
                assertThat(
                        mvc.perform(get(base + "/risk/summary?lookbackDays=730").with(user(email)))
                            .andReturn()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(200)));
    String scenarios =
        json.writeValueAsString(
            Map.of(
                "date",
                "2025-12-31",
                "scenarios",
                java.util.stream.IntStream.range(0, 8)
                    .mapToObj(
                        i ->
                            Map.of(
                                "name",
                                "Illustrative " + i,
                                "commonShockPercent",
                                Integer.toString(-10 * i),
                                "overrides",
                                Map.of()))
                    .toList()));
    results.add(
        measure(
            "stress 8 scenarios",
            100,
            () ->
                assertThat(
                        mvc.perform(
                                post(base + "/risk/stress")
                                    .with(user(email))
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content(scenarios))
                            .andReturn()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(200)));
    results.add(
        measure(
            "history page 20",
            100,
            () ->
                assertThat(
                        mvc.perform(get(base + "/orders?page=0&size=20").with(user(email)))
                            .andReturn()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(200)));
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("recordedAt", Instant.now().toString());
    report.put("jdk", Runtime.version().toString());
    report.put("processors", Runtime.getRuntime().availableProcessors());
    report.put("architecture", System.getProperty("os.arch"));
    report.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
    report.put(
        "cpu",
        Files.lines(Path.of("/proc/cpuinfo"))
            .filter(s -> s.startsWith("model name"))
            .findFirst()
            .orElse("Unavailable"));
    report.put(
        "memory",
        Files.lines(Path.of("/proc/meminfo"))
            .filter(s -> s.startsWith("MemTotal:"))
            .findFirst()
            .orElse("Unavailable"));
    report.put(
        "inputs",
        "Disposable PostgreSQL 17; 3 synthetic assets / 3129 persisted closes; 300 initial"
            + " executions plus 110 measured/warmup buys and 110 FIFO sells; 730 calendar days /"
            + " 521 risk shocks; 8 stress scenarios; 20-row history. In-process MockMvc request,"
            + " transaction, SQL and JSON time; no network or password verification. Serial"
            + " execution, 10 warmups per operation.");
    report.put("results", results);
    Path output = Path.of("target/phase4-benchmark.json");
    Files.writeString(
        output, json.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n");
  }
}
