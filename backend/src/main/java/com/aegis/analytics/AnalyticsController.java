package com.aegis.analytics;

import com.aegis.auth.CurrentUser;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolios/{id}")
public class AnalyticsController {
  private final CurrentUser auth;
  private final AnalyticsService analytics;

  public AnalyticsController(CurrentUser auth, AnalyticsService analytics) {
    this.auth = auth;
    this.analytics = analytics;
  }

  @GetMapping("/valuation")
  ValuationEngine.Snapshot valuation(
      Authentication user,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "2025-12-31") LocalDate date) {
    return analytics.valuation(auth.id(user), id, date);
  }

  @GetMapping("/value-series")
  AnalyticsService.Series series(
      Authentication user,
      @PathVariable UUID id,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    return analytics.series(auth.id(user), id, from, to);
  }

  @GetMapping("/performance")
  AnalyticsService.Performance performance(
      Authentication user,
      @PathVariable UUID id,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    return analytics.performance(auth.id(user), id, from, to);
  }

  @GetMapping("/holdings")
  AnalyticsService.Holdings holdings(
      Authentication user,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "2025-12-31") LocalDate date) {
    return analytics.holdings(auth.id(user), id, date);
  }

  @GetMapping("/allocation")
  AnalyticsService.Allocation allocation(
      Authentication user,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "2025-12-31") LocalDate date) {
    return analytics.allocation(auth.id(user), id, date);
  }
}
