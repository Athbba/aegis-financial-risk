package com.aegis.risk;

import com.aegis.analytics.ValuationEngine;
import com.aegis.analytics.ValuationEngine.*;
import com.aegis.shared.DomainException;
import java.math.*;
import java.time.*;
import java.util.*;

/** Pure historical risk and non-mutating repricing. See docs/PHASE3_RISK_SPEC.md. */
public final class RiskEngine {
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private static final RoundingMode ROUND = RoundingMode.HALF_EVEN;

  private RiskEngine() {}

  public record Metric(String status, BigDecimal value, int observations, int minimum) {}

  public record VaR(
      String status, BigDecimal usd, BigDecimal percent, int observations, int minimum) {}

  public record Shock(
      LocalDate date,
      BigDecimal returnPercent,
      BigDecimal lossUsd,
      Map<String, BigDecimal> assetReturns) {}

  public record Returns(
      String status,
      int candidatePairs,
      int missingPairs,
      List<PriceKey> missingPrices,
      List<Shock> points) {}

  public record Exposure(
      String symbol, BigDecimal marketValue, BigDecimal weightPercent, Metric dailyVolatility) {}

  public record Analysis(
      Returns returns,
      Metric dailyVolatility,
      Metric annualVolatility,
      VaR var95,
      VaR var99,
      List<Exposure> assets,
      BigDecimal concentration) {}

  public record Drawdown(
      String status,
      BigDecimal percent,
      LocalDate peakDate,
      LocalDate troughDate,
      int observations,
      List<LocalDate> missingDates) {}

  public record Scenario(
      String name, BigDecimal commonShockPercent, Map<String, BigDecimal> overrides) {}

  public record StressAsset(
      String symbol,
      BigDecimal shockPercent,
      BigDecimal currentPrice,
      BigDecimal stressedPrice,
      BigDecimal currentValue,
      BigDecimal stressedValue,
      BigDecimal gainLoss) {}

  public record StressResult(
      String name,
      BigDecimal commonShockPercent,
      BigDecimal cash,
      BigDecimal currentValue,
      BigDecimal stressedValue,
      BigDecimal gainLoss,
      BigDecimal gainLossPercent,
      List<StressAsset> assets) {}

  public static boolean weekday(LocalDate d) {
    return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
  }

  private static BigDecimal ratio(BigDecimal a, BigDecimal b) {
    return a.divide(b, 18, ROUND);
  }

  private static BigDecimal pct(BigDecimal a, BigDecimal b) {
    return ValuationEngine.percent(a, b);
  }

  private static List<Position> held(Snapshot s) {
    return s.positions().stream().filter(p -> p.quantity().signum() > 0).toList();
  }

  public static Analysis analyze(Snapshot s, LocalDate from, Map<PriceKey, BigDecimal> prices) {
    var held = held(s);
    String state =
        s.totalValue() == null
            ? "MISSING_PRICES"
            : s.totalValue().signum() == 0
                ? "ZERO_EQUITY"
                : held.isEmpty() ? "CASH_ONLY" : "COMPLETE";
    List<Shock> points = new ArrayList<>();
    Set<PriceKey> missing = new LinkedHashSet<>();
    int candidates = 0, missingPairs = 0;
    if (state.equals("COMPLETE")) {
      LocalDate previous = null;
      for (LocalDate date = from; !date.isAfter(s.date()); date = date.plusDays(1)) {
        if (!weekday(date)) continue;
        if (previous != null) {
          candidates++;
          Map<String, BigDecimal> returns = new LinkedHashMap<>();
          BigDecimal pnl = BigDecimal.ZERO;
          boolean complete = true;
          for (var p : held) {
            var before = new PriceKey(p.symbol(), previous);
            var now = new PriceKey(p.symbol(), date);
            BigDecimal a = prices.get(before), b = prices.get(now);
            if (a == null || a.signum() <= 0) {
              missing.add(before);
              complete = false;
            }
            if (b == null || b.signum() <= 0) {
              missing.add(now);
              complete = false;
            }
            if (a != null && a.signum() > 0 && b != null && b.signum() > 0) {
              BigDecimal r = ratio(b, a).subtract(BigDecimal.ONE);
              returns.put(p.symbol(), r);
              pnl = pnl.add(p.marketValue().multiply(r));
            }
          }
          if (complete)
            points.add(
                new Shock(
                    date,
                    ratio(pnl, s.totalValue()).multiply(HUNDRED),
                    pnl.negate(),
                    Map.copyOf(returns)));
          else missingPairs++;
        }
        previous = date;
      }
      if (!missing.isEmpty()) state = "MISSING_HISTORY";
    }
    int n = points.size();
    List<BigDecimal> portfolioReturns =
        points.stream().map(p -> p.returnPercent().divide(HUNDRED)).toList();
    var losses = points.stream().map(Shock::lossUsd).toList();
    Metric daily = metric(state, portfolioReturns, false),
        annual = metric(state, portfolioReturns, true);
    VaR v95 = tail(state, losses, s.totalValue(), 95),
        v99 = tail(state, losses, s.totalValue(), 99);
    List<Exposure> assets = new ArrayList<>();
    BigDecimal concentration = BigDecimal.ZERO;
    for (var p : held) {
      BigDecimal weight = s.totalValue() == null ? null : pct(p.marketValue(), s.totalValue());
      if (weight != null)
        concentration = concentration.add(ratio(p.marketValue(), s.totalValue()).pow(2));
      String symbol = p.symbol();
      assets.add(
          new Exposure(
              symbol,
              p.marketValue(),
              weight,
              metric(
                  state, points.stream().map(x -> x.assetReturns().get(symbol)).toList(), false)));
    }
    assets.add(
        new Exposure(
            "CASH",
            s.cash(),
            s.totalValue() == null ? null : pct(s.cash(), s.totalValue()),
            new Metric("CASH_ONLY", ZERO, n, 30)));
    return new Analysis(
        new Returns(state, candidates, missingPairs, List.copyOf(missing), List.copyOf(points)),
        daily,
        annual,
        v95,
        v99,
        List.copyOf(assets),
        s.totalValue() == null || s.totalValue().signum() == 0
            ? null
            : concentration.setScale(8, ROUND));
  }

  private static Metric metric(String state, List<BigDecimal> sample, boolean annual) {
    if (state.equals("COMPLETE")) return volatility(sample, annual);
    return new Metric(state, state.equals("CASH_ONLY") ? ZERO : null, sample.size(), 30);
  }

  private static VaR tail(
      String state, List<BigDecimal> losses, BigDecimal equity, int confidence) {
    if (state.equals("COMPLETE")) return var(losses, equity, confidence);
    return new VaR(
        state,
        state.equals("CASH_ONLY") ? ZERO : null,
        state.equals("CASH_ONLY") ? ZERO : null,
        losses.size(),
        confidence == 95 ? 100 : 500);
  }

  public static double sampleDeviation(List<BigDecimal> sample) {
    if (sample.size() < 2)
      throw new IllegalArgumentException("Sample deviation needs two observations");
    double mean = sample.stream().mapToDouble(BigDecimal::doubleValue).average().orElseThrow();
    double sum = 0;
    for (var r : sample) {
      double delta = r.doubleValue() - mean;
      sum += delta * delta;
    }
    return Math.sqrt(sum / (sample.size() - 1));
  }

  public static Metric volatility(List<BigDecimal> sample, boolean annual) {
    if (sample.size() < 30) return new Metric("INSUFFICIENT_OBSERVATIONS", null, sample.size(), 30);
    double value = sampleDeviation(sample) * 100 * (annual ? Math.sqrt(252) : 1);
    return new Metric("COMPLETE", BigDecimal.valueOf(value).setScale(8, ROUND), sample.size(), 30);
  }

  public static VaR var(List<BigDecimal> losses, BigDecimal equity, int confidence) {
    if (confidence != 95 && confidence != 99)
      throw new IllegalArgumentException("Supported confidence: 95 or 99");
    int min = confidence == 95 ? 100 : 500, n = losses.size();
    if (equity.signum() == 0) return new VaR("ZERO_EQUITY", null, null, n, min);
    if (n < min) return new VaR("INSUFFICIENT_OBSERVATIONS", null, null, n, min);
    List<BigDecimal> sorted = losses.stream().sorted().toList();
    int rank = (confidence * n + 99) / 100;
    BigDecimal loss = sorted.get(rank - 1).max(BigDecimal.ZERO);
    return new VaR("COMPLETE", loss.setScale(2, ROUND), pct(loss, equity), n, min);
  }

  public static Drawdown drawdown(LocalDate from, LocalDate to, List<Point> points) {
    Map<LocalDate, Point> byDate = new HashMap<>();
    for (var p : points)
      if (!p.date().isBefore(from) && !p.date().isAfter(to)) byDate.put(p.date(), p);
    List<Point> eligible = new ArrayList<>();
    List<LocalDate> missing = new ArrayList<>();
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      if (!weekday(d)) continue;
      Point p = byDate.get(d);
      if (p == null || p.status() != Status.COMPLETE || p.totalValue() == null) missing.add(d);
      else eligible.add(p);
    }
    if (!missing.isEmpty())
      return new Drawdown(
          "MISSING_VALUATIONS", null, null, null, eligible.size(), List.copyOf(missing));
    if (eligible.size() < 2)
      return new Drawdown(
          "INSUFFICIENT_OBSERVATIONS", null, null, null, eligible.size(), List.of());
    BigDecimal peak = eligible.getFirst().totalValue(), max = BigDecimal.ZERO;
    LocalDate runningPeak = eligible.getFirst().date(),
        peakDate = runningPeak,
        troughDate = runningPeak;
    for (var p : eligible) {
      if (p.totalValue().compareTo(peak) > 0) {
        peak = p.totalValue();
        runningPeak = p.date();
      }
      if (peak.signum() <= 0)
        return new Drawdown("ZERO_EQUITY", null, null, null, eligible.size(), List.of());
      BigDecimal decline = ratio(peak.subtract(p.totalValue()), peak);
      if (decline.compareTo(max) > 0) {
        max = decline;
        peakDate = runningPeak;
        troughDate = p.date();
      }
    }
    return new Drawdown(
        "COMPLETE",
        max.multiply(HUNDRED).setScale(4, ROUND),
        peakDate,
        troughDate,
        eligible.size(),
        List.of());
  }

  public static List<StressResult> stress(Snapshot s, List<Scenario> scenarios) {
    if (s.totalValue() == null)
      throw DomainException.invalid(
          "MISSING_PRICES", "Stress requires exact selected-date prices for all holdings.");
    if (scenarios == null || scenarios.isEmpty() || scenarios.size() > 8) throw invalidScenario();
    var held = held(s);
    Set<String> symbols = new HashSet<>();
    for (var p : held) symbols.add(p.symbol());
    List<StressResult> results = new ArrayList<>();
    for (var scenario : scenarios) {
      if (scenario == null
          || scenario.name() == null
          || scenario.name().isBlank()
          || scenario.name().length() > 60
          || scenario.overrides() == null
          || scenario.overrides().size() > 100
          || !symbols.containsAll(scenario.overrides().keySet())) throw invalidScenario();
      checkShock(scenario.commonShockPercent());
      for (var shock : scenario.overrides().values()) checkShock(shock);
      List<StressAsset> assets = new ArrayList<>();
      BigDecimal total = s.cash();
      for (var p : held) {
        BigDecimal shock =
            scenario.overrides().getOrDefault(p.symbol(), scenario.commonShockPercent());
        BigDecimal price = p.valuationPrice().multiply(BigDecimal.ONE.add(shock.divide(HUNDRED)));
        if (price.signum() < 0) throw invalidScenario();
        BigDecimal value = p.quantity().multiply(price).setScale(2, ROUND);
        total = total.add(value);
        assets.add(
            new StressAsset(
                p.symbol(),
                shock,
                p.valuationPrice(),
                price,
                p.marketValue(),
                value,
                value.subtract(p.marketValue())));
      }
      BigDecimal change = total.subtract(s.totalValue());
      results.add(
          new StressResult(
              scenario.name(),
              scenario.commonShockPercent(),
              s.cash(),
              s.totalValue(),
              total,
              change,
              pct(change, s.totalValue()),
              List.copyOf(assets)));
    }
    return List.copyOf(results);
  }

  private static void checkShock(BigDecimal s) {
    if (s == null || s.scale() > 4 || s.compareTo(HUNDRED.negate()) < 0 || s.compareTo(HUNDRED) > 0)
      throw invalidScenario();
  }

  private static DomainException invalidScenario() {
    return DomainException.invalid(
        "INVALID_SCENARIO",
        "Use 1–8 named scenarios, held-symbol overrides and shocks from -100% to +100% with at most"
            + " 4 decimals.");
  }
}
