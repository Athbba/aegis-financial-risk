package com.aegis.operations;

import io.micrometer.core.instrument.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTelemetry extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestTelemetry.class);
  private final MeterRegistry metrics;

  public RequestTelemetry(MeterRegistry metrics) {
    this.metrics = metrics;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    long start = System.nanoTime();
    MDC.put("requestId", id);
    res.setHeader("X-Request-ID", id);
    try {
      chain.doFilter(req, res);
    } finally {
      Object matched = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      String route = matched instanceof String path ? path : "UNMAPPED";
      String method =
          switch (req.getMethod()) {
            case "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD" -> req.getMethod();
            default -> "OTHER";
          };
      int status = res.getStatus();
      String outcome =
          status >= 500 ? "infrastructure_failure" : status >= 400 ? "rejected" : "success";
      Timer.builder("aegis.requests")
          .tags("route", route, "method", method, "outcome", outcome)
          .register(metrics)
          .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
      // Only bounded route templates and status: no raw path/query, body, identity or headers.
      log.info(
          "request_complete method={} route={} status={} outcome={}",
          method,
          route,
          status,
          outcome);
      MDC.remove("requestId");
    }
  }
}
