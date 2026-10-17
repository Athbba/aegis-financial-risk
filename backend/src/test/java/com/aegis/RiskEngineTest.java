package com.aegis;

import static org.assertj.core.api.Assertions.*;

import com.aegis.analytics.ValuationEngine;
import com.aegis.analytics.ValuationEngine.*;
import com.aegis.risk.RiskEngine;
import com.aegis.risk.RiskEngine.*;
import com.aegis.shared.DomainException;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RiskEngineTest {
  static BigDecimal d(String s) {
    return new BigDecimal(s);
  }

  static final LocalDate FRI = LocalDate.of(2024, 1, 5), MON = FRI.plusDays(3);

  static Snapshot snapshot(LocalDate date) {
    return ValuationEngine.timeline(
            d("1000"),
            date,
            date,
            List.of(
                new Event(FRI, "A", d("3"), d("-300"), d("300"), d("0")),
                new Event(FRI, "B", d("10"), d("-200"), d("200"), d("0"))),
            Map.of(new PriceKey("A", date), d("100"), new PriceKey("B", date), d("20")))
        .last();
  }

  static Snapshot cash(LocalDate date) {
    return ValuationEngine.timeline(d("500"), date, date, List.of(), Map.of()).last();
  }

  @Test
  void manuallyCalculatedSampleDeviationAndAnnualization() {
    assertThat(RiskEngine.sampleDeviation(List.of(d(".01"), d(".02"), d(".03"))))
        .isCloseTo(.01, within(1e-10));
    List<BigDecimal> sample = new ArrayList<>();
    for (int i = 0; i < 10; i++) sample.addAll(List.of(d(".01"), d(".02"), d(".03")));
    assertThat(RiskEngine.volatility(sample, false).value().doubleValue())
        .isCloseTo(.8304547985373997, within(1e-7));
    assertThat(RiskEngine.volatility(sample, true).value().doubleValue())
        .isCloseTo(13.183061232061236, within(1e-7));
    assertThat(RiskEngine.volatility(sample.subList(0, 29), false).value()).isNull();
  }

  @Test
  void alignsWeekdaysAndFixedExposureWeightsIncludingCash() {
    var prices =
        Map.of(
            new PriceKey("A", FRI),
            d("100"),
            new PriceKey("B", FRI),
            d("100"),
            new PriceKey("A", MON),
            d("110"),
            new PriceKey("B", MON),
            d("95"),
            new PriceKey("A", MON.plusDays(1)),
            d("99999"));
    var a = RiskEngine.analyze(snapshot(MON), FRI, prices);
    assertThat(a.returns().status()).isEqualTo("COMPLETE");
    assertThat(a.returns().points())
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.returnPercent()).isEqualByComparingTo("2");
              assertThat(p.lossUsd()).isEqualByComparingTo("-20");
            });
    assertThat(a.concentration()).isEqualByComparingTo(".13");
    assertThat(a.assets())
        .extracting(Exposure::weightPercent)
        .containsExactly(d("30.0000"), d("20.0000"), d("50.0000"));
    assertThat(a.dailyVolatility().status()).isEqualTo("INSUFFICIENT_OBSERVATIONS");
    assertThat(a.var95().usd()).isNull();
  }

  @Test
  void missingWeekdayInvalidatesAdjacentPairsAndMetricsWithoutBridging() {
    LocalDate end = MON.plusDays(2);
    Map<PriceKey, BigDecimal> prices = new HashMap<>();
    for (String symbol : List.of("A", "B"))
      for (LocalDate date : List.of(FRI, MON, MON.plusDays(1), end))
        prices.put(new PriceKey(symbol, date), d("100"));
    prices.remove(new PriceKey("B", MON.plusDays(1)));
    var a = RiskEngine.analyze(snapshot(end), FRI, prices);
    assertThat(a.returns().candidatePairs()).isEqualTo(3);
    assertThat(a.returns().missingPairs()).isEqualTo(2);
    assertThat(a.returns().points()).hasSize(1);
    assertThat(a.returns().missingPrices()).containsExactly(new PriceKey("B", MON.plusDays(1)));
    assertThat(a.dailyVolatility().status()).isEqualTo("MISSING_HISTORY");
  }

  @Test
  void nearestRankAndMinimumsAndGainFloor() {
    List<BigDecimal> losses = new ArrayList<>();
    for (int i = 1; i <= 500; i++) losses.add(BigDecimal.valueOf(i));
    Collections.reverse(losses);
    assertThat(RiskEngine.var(losses, d("1000"), 95).usd()).isEqualByComparingTo("475");
    assertThat(RiskEngine.var(losses, d("1000"), 99).usd()).isEqualByComparingTo("495");
    assertThat(RiskEngine.var(losses, d("1000"), 99).percent()).isEqualByComparingTo("49.5");
    assertThat(RiskEngine.var(losses.subList(400, 500), d("1000"), 95).usd())
        .isEqualByComparingTo("95");
    assertThat(RiskEngine.var(losses.subList(0, 99), d("1000"), 95).usd()).isNull();
    assertThat(RiskEngine.var(losses.subList(0, 499), d("1000"), 99).usd()).isNull();
    assertThat(RiskEngine.var(Collections.nCopies(500, d("-1")), d("1000"), 99).usd()).isZero();
    assertThat(RiskEngine.var(losses, d("0"), 95).status()).isEqualTo("ZERO_EQUITY");
  }

  static List<Point> equity(String... values) {
    List<Point> result = new ArrayList<>();
    LocalDate date = MON;
    for (String value : values) {
      while (!RiskEngine.weekday(date)) date = date.plusDays(1);
      result.add(
          new Point(
              date,
              value == null ? Status.MISSING_PRICES : Status.COMPLETE,
              d("0"),
              null,
              value == null ? null : d(value),
              null,
              null,
              null,
              null,
              List.of()));
      date = date.plusDays(1);
    }
    return result;
  }

  static Drawdown draw(List<Point> p) {
    return RiskEngine.drawdown(p.getFirst().date(), p.getLast().date(), p);
  }

  @Test
  void maximumDrawdownPrecedesFinalRecoveryAndUsesActualEquity() {
    var points = equity("100", "120", "90", "110", "80", "130");
    var dd = draw(points);
    assertThat(dd.percent()).isEqualByComparingTo("33.3333");
    assertThat(dd.peakDate()).isEqualTo(points.get(1).date());
    assertThat(dd.troughDate()).isEqualTo(points.get(4).date());
    assertThat(draw(equity("100", "100")).percent()).isZero();
    assertThat(draw(equity("100", "110")).percent()).isZero();
    assertThat(draw(equity("100", "80")).percent()).isEqualByComparingTo("20");
    assertThat(draw(equity("100", null, "80")).status()).isEqualTo("MISSING_VALUATIONS");
    assertThat(draw(equity("0", "0")).status()).isEqualTo("ZERO_EQUITY");
    assertThat(draw(equity("100")).status()).isEqualTo("INSUFFICIENT_OBSERVATIONS");
    var missing = new ArrayList<>(points);
    missing.remove(2);
    assertThat(draw(missing).status()).isEqualTo("MISSING_VALUATIONS");
  }

  @Test
  void stressTwoAssetsCashAndZeroShockExactlyReconcile() {
    var s = snapshot(MON);
    var result =
        RiskEngine.stress(
            s,
            List.of(
                new Scenario("Mixed", d("-10"), Map.of("B", d("20"))),
                new Scenario("Zero", d("0"), Map.of()),
                new Scenario("Wipeout", d("-100"), Map.of())));
    assertThat(result.getFirst().stressedValue()).isEqualByComparingTo("1010");
    assertThat(result.getFirst().gainLoss()).isEqualByComparingTo("10");
    assertThat(result.getFirst().gainLossPercent()).isEqualByComparingTo("1");
    assertThat(result.getFirst().assets())
        .extracting(StressAsset::gainLoss)
        .containsExactly(d("-30.00"), d("40.00"));
    assertThat(result.get(1).gainLoss()).isZero();
    assertThat(result.get(2).stressedValue()).isEqualByComparingTo("500");
    assertThatThrownBy(
            () -> RiskEngine.stress(s, List.of(new Scenario("Bad", d("-100.1"), Map.of()))))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () ->
                RiskEngine.stress(
                    s, List.of(new Scenario("Bad", d("0"), Map.of("UNKNOWN", d("1"))))))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () -> RiskEngine.stress(s, List.of(new Scenario("Bad", d("0.00001"), Map.of()))))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void cashOnlyIsAnalyticallyZeroWithNoInventedObservations() {
    var a = RiskEngine.analyze(cash(MON), FRI, Map.of());
    assertThat(a.dailyVolatility().status()).isEqualTo("CASH_ONLY");
    assertThat(a.dailyVolatility().value()).isZero();
    assertThat(a.var99().usd()).isZero();
    assertThat(a.returns().points()).isEmpty();
    assertThat(a.concentration()).isZero();
    assertThat(
            RiskEngine.stress(cash(MON), List.of(new Scenario("Test", d("-25"), Map.of())))
                .getFirst()
                .gainLoss())
        .isZero();
  }

  @Test
  void missingCurrentPricesProduceExplicitUnavailableAndNoStress() {
    var s =
        ValuationEngine.timeline(
                d("1000"),
                MON,
                MON,
                List.of(new Event(FRI, "A", d("1"), d("-10"), d("10"), d("0"))),
                Map.of())
            .last();
    assertThat(RiskEngine.analyze(s, FRI, Map.of()).var95().status()).isEqualTo("MISSING_PRICES");
    assertThatThrownBy(() -> RiskEngine.stress(s, List.of(new Scenario("Test", d("0"), Map.of()))))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void missingHistoryRemainsUnavailableEvenWhenVolatilitySampleIsLargeEnough() {
    LocalDate from = LocalDate.of(2024, 1, 2), end = LocalDate.of(2024, 3, 1);
    Map<PriceKey, BigDecimal> prices = new HashMap<>();
    for (LocalDate date = from; !date.isAfter(end); date = date.plusDays(1))
      if (RiskEngine.weekday(date))
        for (String symbol : List.of("A", "B")) prices.put(new PriceKey(symbol, date), d("100"));
    prices.remove(new PriceKey("A", LocalDate.of(2024, 2, 1)));
    var a = RiskEngine.analyze(snapshot(end), from, prices);
    assertThat(a.returns().points().size()).isGreaterThan(30);
    assertThat(a.dailyVolatility().status()).isEqualTo("MISSING_HISTORY");
    assertThat(a.dailyVolatility().value()).isNull();
  }

  @Test
  void zeroEquityAndAggregateCentRoundingAreExplicit() {
    var zero =
        ValuationEngine.timeline(
                d(".01"),
                MON,
                MON,
                List.of(new Event(FRI, "A", d("1"), d("-.01"), d(".01"), d("0"))),
                Map.of(new PriceKey("A", MON), d(".0001")))
            .last();
    assertThat(zero.totalValue()).isZero();
    var a = RiskEngine.analyze(zero, FRI, Map.of());
    assertThat(a.var95().status()).isEqualTo("ZERO_EQUITY");
    assertThat(a.var95().percent()).isNull();
    var stress =
        RiskEngine.stress(zero, List.of(new Scenario("Zero", d("0"), Map.of()))).getFirst();
    assertThat(stress.gainLoss()).isZero();
    assertThat(stress.gainLossPercent()).isNull();
    var cents =
        ValuationEngine.timeline(
                d("1"),
                MON,
                MON,
                List.of(new Event(FRI, "A", d("3"), d("-.03"), d(".03"), d("0"))),
                Map.of(new PriceKey("A", MON), d(".01")))
            .last();
    var changed =
        RiskEngine.stress(cents, List.of(new Scenario("Half even", d("50"), Map.of()))).getFirst();
    assertThat(changed.assets().getFirst().stressedValue()).isEqualByComparingTo(".04");
    assertThat(changed.stressedValue()).isEqualByComparingTo("1.01");
    assertThat(
            RiskEngine.stress(cents, List.of(new Scenario("Zero", d("0"), Map.of())))
                .getFirst()
                .gainLoss())
        .isZero();
  }

  @Test
  void rejectsNullEmptyOversizedAndUnheldScenarioInputs() {
    for (List<Scenario> scenarios :
        Arrays.asList(
            null,
            List.<Scenario>of(),
            Arrays.<Scenario>asList((Scenario) null),
            Collections.nCopies(9, new Scenario("Nine", d("0"), Map.of())),
            List.of(new Scenario(" ", d("0"), Map.of())),
            List.of(new Scenario("Null", null, Map.of())),
            List.of(new Scenario("Null", d("0"), null))))
      assertThatThrownBy(() -> RiskEngine.stress(snapshot(MON), scenarios))
          .isInstanceOf(DomainException.class);
    Map<String, BigDecimal> invalid = new HashMap<>();
    invalid.put("A", null);
    assertThatThrownBy(
            () ->
                RiskEngine.stress(
                    snapshot(MON), List.of(new Scenario("Null override", d("0"), invalid))))
        .isInstanceOf(DomainException.class);
  }
}
