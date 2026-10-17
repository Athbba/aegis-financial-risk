package com.aegis.analytics;

import com.aegis.shared.DomainException;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Pure end-of-day accounting. No persistence, HTTP, current lots, or binary floating point. */
public final class ValuationEngine {
  private static final BigDecimal ZERO = new BigDecimal("0.00");

  public enum Status {
    COMPLETE,
    MISSING_PRICES
  }

  public record Event(
      LocalDate date,
      String symbol,
      BigDecimal quantityDelta,
      BigDecimal cashDelta,
      BigDecimal costDelta,
      BigDecimal realizedPnl) {}

  public record PriceKey(String symbol, LocalDate date) {}

  public record Position(
      String symbol,
      BigDecimal quantity,
      BigDecimal costBasis,
      BigDecimal valuationPrice,
      BigDecimal marketValue,
      BigDecimal realizedPnl,
      BigDecimal unrealizedPnl,
      BigDecimal totalPnl,
      BigDecimal unrealizedReturnPercent) {}

  public record Snapshot(
      LocalDate date,
      Status status,
      BigDecimal initialCapital,
      BigDecimal cash,
      BigDecimal marketValue,
      BigDecimal totalValue,
      BigDecimal realizedPnl,
      BigDecimal unrealizedPnl,
      BigDecimal totalPnl,
      BigDecimal returnPercent,
      List<String> missingSymbols,
      List<Position> positions) {}

  public record Point(
      LocalDate date,
      Status status,
      BigDecimal cash,
      BigDecimal marketValue,
      BigDecimal totalValue,
      BigDecimal realizedPnl,
      BigDecimal unrealizedPnl,
      BigDecimal totalPnl,
      BigDecimal returnPercent,
      List<String> missingSymbols) {
    static Point of(Snapshot s) {
      return new Point(
          s.date(),
          s.status(),
          s.cash(),
          s.marketValue(),
          s.totalValue(),
          s.realizedPnl(),
          s.unrealizedPnl(),
          s.totalPnl(),
          s.returnPercent(),
          s.missingSymbols());
    }
  }

  public record Timeline(Snapshot first, Snapshot last, List<Point> points) {}

  public record Period(
      BigDecimal startingValue,
      BigDecimal endingValue,
      BigDecimal change,
      BigDecimal returnPercent,
      String status) {}

  private static final class Balance {
    BigDecimal quantity = BigDecimal.ZERO, cost = ZERO, realized = ZERO;
  }

  private ValuationEngine() {}

  public static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
    if (denominator.signum() == 0) return null;
    return numerator.multiply(new BigDecimal("100")).divide(denominator, 4, RoundingMode.HALF_EVEN);
  }

  public static Period period(Snapshot start, Snapshot end) {
    if (start.totalValue() == null || end.totalValue() == null)
      return new Period(start.totalValue(), end.totalValue(), null, null, "MISSING_ENDPOINT_PRICE");
    BigDecimal change = end.totalValue().subtract(start.totalValue());
    BigDecimal percent = percent(change, start.totalValue());
    return new Period(
        start.totalValue(),
        end.totalValue(),
        change,
        percent,
        percent == null ? "ZERO_START_VALUE" : "COMPLETE");
  }

  public static Timeline timeline(
      BigDecimal initialCapital,
      LocalDate from,
      LocalDate to,
      List<Event> events,
      Map<PriceKey, BigDecimal> prices) {
    if (initialCapital.signum() <= 0) throw invalidHistory();
    if (from.isAfter(to))
      throw DomainException.invalid("INVALID_DATE_RANGE", "Start date must not follow end date.");
    List<Event> ordered = events.stream().sorted(Comparator.comparing(Event::date)).toList();
    Map<String, Balance> balances = new TreeMap<>();
    BigDecimal cash = initialCapital;
    int cursor = 0;
    Snapshot first = null, last = null;
    List<Point> points = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      while (cursor < ordered.size() && !ordered.get(cursor).date().isAfter(date)) {
        Event event = ordered.get(cursor++);
        Balance balance = balances.computeIfAbsent(event.symbol(), key -> new Balance());
        balance.quantity = balance.quantity.add(event.quantityDelta());
        balance.cost = balance.cost.add(event.costDelta());
        balance.realized = balance.realized.add(event.realizedPnl());
        cash = cash.add(event.cashDelta());
      }
      last = snapshot(date, initialCapital, cash, balances, prices);
      if (first == null) first = last;
      points.add(Point.of(last));
    }
    return new Timeline(first, last, List.copyOf(points));
  }

  private static Snapshot snapshot(
      LocalDate date,
      BigDecimal capital,
      BigDecimal cash,
      Map<String, Balance> balances,
      Map<PriceKey, BigDecimal> prices) {
    if (cash.signum() < 0) throw invalidHistory();
    List<Position> positions = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    BigDecimal totalMarket = ZERO, totalCost = ZERO, realized = ZERO;
    for (var entry : balances.entrySet()) {
      String symbol = entry.getKey();
      Balance balance = entry.getValue();
      if (balance.quantity.signum() < 0
          || balance.cost.signum() < 0
          || (balance.quantity.signum() == 0 && balance.cost.signum() != 0)) throw invalidHistory();
      boolean open = balance.quantity.signum() > 0;
      BigDecimal price = open ? prices.get(new PriceKey(symbol, date)) : null;
      if (price != null && price.signum() <= 0) throw invalidHistory();
      BigDecimal market =
          !open
              ? ZERO
              : price == null
                  ? null
                  : balance.quantity.multiply(price).setScale(2, RoundingMode.HALF_EVEN);
      BigDecimal unrealized = market == null ? null : market.subtract(balance.cost);
      if (market == null) missing.add(symbol);
      else totalMarket = totalMarket.add(market);
      totalCost = totalCost.add(balance.cost);
      realized = realized.add(balance.realized);
      positions.add(
          new Position(
              symbol,
              balance.quantity,
              balance.cost,
              price,
              market,
              balance.realized,
              unrealized,
              unrealized == null ? null : balance.realized.add(unrealized),
              unrealized == null ? null : percent(unrealized, balance.cost)));
    }
    if (!missing.isEmpty())
      return new Snapshot(
          date,
          Status.MISSING_PRICES,
          capital,
          cash,
          null,
          null,
          realized,
          null,
          null,
          null,
          List.copyOf(missing),
          List.copyOf(positions));
    BigDecimal total = cash.add(totalMarket);
    BigDecimal unrealized = totalMarket.subtract(totalCost);
    BigDecimal pnl = realized.add(unrealized);
    if (pnl.compareTo(total.subtract(capital)) != 0) throw invalidHistory();
    return new Snapshot(
        date,
        Status.COMPLETE,
        capital,
        cash,
        totalMarket,
        total,
        realized,
        unrealized,
        pnl,
        percent(pnl, capital),
        List.of(),
        List.copyOf(positions));
  }

  private static DomainException invalidHistory() {
    return DomainException.invalid(
        "ACCOUNTING_DATA_INCONSISTENT", "Stored accounting records cannot be reconciled.");
  }
}
