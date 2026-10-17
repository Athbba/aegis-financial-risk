package com.aegis.risk;

import com.aegis.analytics.*;
import com.aegis.portfolio.PortfolioService;
import com.aegis.shared.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
public class RiskService {
  public record Summary(
      LocalDate from,
      LocalDate date,
      int lookbackDays,
      String currency,
      ValuationEngine.Snapshot valuation,
      RiskEngine.Analysis analysis,
      RiskEngine.Drawdown drawdown) {}

  public record StressRequest(LocalDate date, List<RiskEngine.Scenario> scenarios) {}

  private final PortfolioService portfolios;
  private final AnalyticsRepository repository;
  private final AnalyticsService analytics;

  public RiskService(
      PortfolioService portfolios, AnalyticsRepository repository, AnalyticsService analytics) {
    this.portfolios = portfolios;
    this.repository = repository;
    this.analytics = analytics;
  }

  public Summary summary(UUID user, UUID id, LocalDate date, int lookbackDays) {
    var portfolio = portfolios.owned(user, id);
    if (date == null
        || date.isBefore(portfolio.getSimulationStartDate())
        || date.isAfter(SimulationCalendar.END)
        || lookbackDays < 2
        || lookbackDays > 1461)
      throw DomainException.invalid(
          "INVALID_RISK_RANGE", "Choose a dataset date and a lookback of 2–1461 calendar days.");
    LocalDate from = date.minusDays(lookbackDays - 1L);
    if (from.isBefore(portfolio.getSimulationStartDate()))
      from = portfolio.getSimulationStartDate();
    var events = repository.events(id, date);
    var symbols = events.stream().map(ValuationEngine.Event::symbol).collect(Collectors.toSet());
    var prices = repository.prices(symbols, from, date);
    var timeline = ValuationEngine.timeline(repository.funding(id), from, date, events, prices);
    return new Summary(
        from,
        date,
        lookbackDays,
        "USD",
        timeline.last(),
        RiskEngine.analyze(timeline.last(), from, prices),
        RiskEngine.drawdown(from, date, timeline.points()));
  }

  public List<RiskEngine.StressResult> stress(UUID user, UUID id, StressRequest request) {
    portfolios.owned(user, id);
    if (request == null || request.date() == null)
      throw DomainException.invalid("INVALID_INPUT", "A valuation date is required.");
    return RiskEngine.stress(analytics.valuation(user, id, request.date()), request.scenarios());
  }
}
