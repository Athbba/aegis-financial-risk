package com.aegis.analytics;

import com.aegis.portfolio.*;
import com.aegis.shared.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
public class AnalyticsService {
  public static final int MAX_DAYS = 1461;

  public record Series(
      LocalDate from,
      LocalDate to,
      LocalDate simulationStartDate,
      String currency,
      BigDecimal initialCapital,
      int completeDays,
      int missingDays,
      List<ValuationEngine.Point> points) {}

  public record AllocationItem(String symbol, BigDecimal marketValue, BigDecimal weightPercent) {}

  public record Allocation(
      LocalDate date, String status, List<String> missingSymbols, List<AllocationItem> items) {}

  public record Holdings(
      LocalDate date,
      ValuationEngine.Status status,
      List<String> missingSymbols,
      List<ValuationEngine.Position> positions) {}

  public record Performance(
      LocalDate simulationStartDate,
      String currency,
      ValuationEngine.Snapshot summary,
      ValuationEngine.Period period,
      Series series,
      Allocation allocation) {}

  private final PortfolioService portfolios;
  private final AnalyticsRepository repository;

  public AnalyticsService(PortfolioService portfolios, AnalyticsRepository repository) {
    this.portfolios = portfolios;
    this.repository = repository;
  }

  public ValuationEngine.Snapshot valuation(UUID user, UUID id, LocalDate date) {
    var portfolio = portfolios.owned(user, id);
    validate(portfolio, date, date);
    return load(id, date, date).last();
  }

  public Series series(UUID user, UUID id, LocalDate from, LocalDate to) {
    var portfolio = portfolios.owned(user, id);
    validate(portfolio, from, to);
    return series(portfolio, from, to, load(id, from, to));
  }

  public Performance performance(UUID user, UUID id, LocalDate from, LocalDate to) {
    var portfolio = portfolios.owned(user, id);
    validate(portfolio, from, to);
    var timeline = load(id, from, to);
    return new Performance(
        portfolio.getSimulationStartDate(),
        "USD",
        timeline.last(),
        ValuationEngine.period(timeline.first(), timeline.last()),
        series(portfolio, from, to, timeline),
        allocation(timeline.last()));
  }

  public Holdings holdings(UUID user, UUID id, LocalDate date) {
    var snapshot = valuation(user, id, date);
    return new Holdings(date, snapshot.status(), snapshot.missingSymbols(), snapshot.positions());
  }

  public Allocation allocation(UUID user, UUID id, LocalDate date) {
    return allocation(valuation(user, id, date));
  }

  private ValuationEngine.Timeline load(UUID id, LocalDate from, LocalDate to) {
    var funding = repository.funding(id);
    var events = repository.events(id, to);
    var symbols = events.stream().map(ValuationEngine.Event::symbol).collect(Collectors.toSet());
    var prices = repository.prices(symbols, from, to);
    return ValuationEngine.timeline(funding, from, to, events, prices);
  }

  private Series series(
      Portfolio portfolio, LocalDate from, LocalDate to, ValuationEngine.Timeline timeline) {
    int complete =
        (int)
            timeline.points().stream()
                .filter(p -> p.status() == ValuationEngine.Status.COMPLETE)
                .count();
    return new Series(
        from,
        to,
        portfolio.getSimulationStartDate(),
        "USD",
        timeline.last().initialCapital(),
        complete,
        timeline.points().size() - complete,
        timeline.points());
  }

  private Allocation allocation(ValuationEngine.Snapshot snapshot) {
    var items = new ArrayList<AllocationItem>();
    boolean complete = snapshot.status() == ValuationEngine.Status.COMPLETE;
    BigDecimal total = snapshot.totalValue();
    items.add(
        new AllocationItem(
            "CASH",
            snapshot.cash(),
            complete ? ValuationEngine.percent(snapshot.cash(), total) : null));
    for (var position : snapshot.positions())
      if (position.quantity().signum() > 0)
        items.add(
            new AllocationItem(
                position.symbol(),
                position.marketValue(),
                complete ? ValuationEngine.percent(position.marketValue(), total) : null));
    return new Allocation(
        snapshot.date(),
        !complete ? "MISSING_PRICES" : total.signum() == 0 ? "ZERO_TOTAL_VALUE" : "COMPLETE",
        snapshot.missingSymbols(),
        List.copyOf(items));
  }

  private void validate(Portfolio portfolio, LocalDate from, LocalDate to) {
    if (from == null
        || to == null
        || from.isAfter(to)
        || to.isAfter(SimulationCalendar.END)
        || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS)
      throw DomainException.invalid(
          "INVALID_DATE_RANGE",
          "Choose an ordered range of at most 1,461 calendar days ending by 2025-12-31.");
    if (from.isBefore(portfolio.getSimulationStartDate()))
      throw DomainException.invalid(
          "BEFORE_SIMULATION_START",
          "This portfolio's initial capital is available from "
              + portfolio.getSimulationStartDate()
              + " only.");
  }
}
