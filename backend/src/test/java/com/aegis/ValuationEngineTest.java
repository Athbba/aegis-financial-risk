package com.aegis;

import static org.assertj.core.api.Assertions.*;

import com.aegis.analytics.ValuationEngine;
import com.aegis.analytics.ValuationEngine.*;
import com.aegis.shared.DomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class ValuationEngineTest {
  private static BigDecimal d(String x) {
    return new BigDecimal(x);
  }

  private static LocalDate day(int x) {
    return LocalDate.of(2024, 1, x);
  }

  private static Event event(
      int day, String symbol, String quantity, String cash, String cost, String realized) {
    return new Event(day(day), symbol, d(quantity), d(cash), d(cost), d(realized));
  }

  private static List<Event> scenario() {
    return List.of(event(2, "A", "10", "-100", "100", "0"), event(3, "A", "-4", "60", "-40", "20"));
  }

  private static Snapshot value(int date, List<Event> events, Map<PriceKey, BigDecimal> prices) {
    return ValuationEngine.timeline(d("1000.00"), day(date), day(date), events, prices).last();
  }

  @Test
  void requiredManuallyVerifiableScenario() {
    var s = value(4, scenario(), Map.of(new PriceKey("A", day(4)), d("12")));
    assertThat(s.cash()).isEqualByComparingTo("960");
    assertThat(s.marketValue()).isEqualByComparingTo("72");
    assertThat(s.totalValue()).isEqualByComparingTo("1032");
    assertThat(s.positions().getFirst().costBasis()).isEqualByComparingTo("60");
    assertThat(s.realizedPnl()).isEqualByComparingTo("20");
    assertThat(s.unrealizedPnl()).isEqualByComparingTo("12");
    assertThat(s.totalPnl()).isEqualByComparingTo("32");
    assertThat(s.returnPercent()).isEqualByComparingTo("3.2000");
    assertThat(s.positions().getFirst().unrealizedReturnPercent()).isEqualByComparingTo("20");
  }

  @Test
  void initialFundingNeverBecomesAProfit() {
    var timeline = ValuationEngine.timeline(d("1000"), day(1), day(8), List.of(), Map.of());
    assertThat(timeline.points())
        .hasSize(8)
        .allSatisfy(
            p -> {
              assertThat(p.totalValue()).isEqualByComparingTo("1000");
              assertThat(p.totalPnl()).isEqualByComparingTo("0");
              assertThat(p.returnPercent()).isEqualByComparingTo("0");
              assertThat(p.status()).isEqualTo(Status.COMPLETE);
            });
  }

  @Test
  void futureBuysAndSellsAreExcludedFromPastPositionsAndCash() {
    var before = value(1, scenario(), Map.of());
    assertThat(before.positions()).isEmpty();
    assertThat(before.cash()).isEqualByComparingTo("1000");
    var afterBuy = value(2, scenario(), Map.of(new PriceKey("A", day(2)), d("10")));
    assertThat(afterBuy.cash()).isEqualByComparingTo("900");
    assertThat(afterBuy.positions().getFirst().quantity()).isEqualByComparingTo("10");
    assertThat(afterBuy.realizedPnl()).isEqualByComparingTo("0");
  }

  @Test
  void missingPricesMakeEquityUnknownButKeepCashAndRealizedProfitKnown() {
    var s = value(4, scenario(), Map.of());
    assertThat(s.status()).isEqualTo(Status.MISSING_PRICES);
    assertThat(s.missingSymbols()).containsExactly("A");
    assertThat(s.cash()).isEqualByComparingTo("960");
    assertThat(s.realizedPnl()).isEqualByComparingTo("20");
    assertThat(s.marketValue()).isNull();
    assertThat(s.totalValue()).isNull();
    assertThat(s.returnPercent()).isNull();
    assertThat(s.positions().getFirst().valuationPrice()).isNull();
  }

  @Test
  void oneMissingAssetDoesNotProduceAnIncompletePortfolioTotal() {
    var events = new ArrayList<>(scenario());
    events.add(event(4, "B", "1", "-20", "20", "0"));
    var s = value(4, events, Map.of(new PriceKey("A", day(4)), d("12")));
    assertThat(s.missingSymbols()).containsExactly("B");
    assertThat(s.marketValue()).isNull();
    assertThat(s.positions().getFirst().marketValue()).isEqualByComparingTo("72");
  }

  @Test
  void closedPositionsNeedNoPriceAndRetainTheirRealizedPnl() {
    var events = new ArrayList<>(scenario());
    events.add(event(4, "A", "-6", "72", "-60", "12"));
    var s = value(4, events, Map.of());
    assertThat(s.status()).isEqualTo(Status.COMPLETE);
    assertThat(s.marketValue()).isEqualByComparingTo("0");
    assertThat(s.realizedPnl()).isEqualByComparingTo("32");
    assertThat(s.totalValue()).isEqualByComparingTo("1032");
    assertThat(s.positions().getFirst().quantity()).isZero();
  }

  @Test
  void sameDayAggregationIsIndependentOfTiedRecordOrder() {
    var events =
        List.of(
            event(2, "A", "10", "-100", "100", "0"),
            event(2, "A", "-4", "40", "-40", "0"),
            event(2, "A", "2", "-20", "20", "0"));
    var reversed = new ArrayList<>(events);
    Collections.reverse(reversed);
    var prices = Map.of(new PriceKey("A", day(2)), d("10"));
    assertThat(value(2, events, prices)).isEqualTo(value(2, reversed, prices));
    assertThat(value(2, events, prices).positions().getFirst().quantity())
        .isEqualByComparingTo("8");
  }

  @Test
  void weekendsAreGapsRatherThanCarriedForwardCloses() {
    var prices = Map.of(new PriceKey("A", day(5)), d("12"), new PriceKey("A", day(8)), d("13"));
    var timeline = ValuationEngine.timeline(d("1000"), day(5), day(8), scenario(), prices);
    assertThat(timeline.points().stream().map(Point::totalValue))
        .containsExactly(d("1032.00"), null, null, d("1038.00"));
  }

  @Test
  void negativePerformanceIsNotClampedToZero() {
    var s =
        value(
            4,
            List.of(event(2, "A", "10", "-100", "100", "0")),
            Map.of(new PriceKey("A", day(4)), d("5")));
    assertThat(s.totalPnl()).isEqualByComparingTo("-50");
    assertThat(s.returnPercent()).isEqualByComparingTo("-5");
  }

  @Test
  void positionMarketValuesRoundHalfEvenBeforeAggregation() {
    var s =
        value(
            4,
            List.of(event(2, "A", "1", "-1", "1", "0"), event(2, "B", "1", "-1", "1", "0")),
            Map.of(new PriceKey("A", day(4)), d("1.0050"), new PriceKey("B", day(4)), d("1.0150")));
    assertThat(s.positions().getFirst().marketValue()).isEqualByComparingTo("1.00");
    assertThat(s.marketValue()).isEqualByComparingTo("2.02");
    assertThat(s.totalPnl()).isEqualByComparingTo("0.02");
  }

  @Test
  void periodReturnUsesStartingWealthRatherThanDeposit() {
    var timeline =
        ValuationEngine.timeline(
            d("1000"),
            day(3),
            day(4),
            scenario(),
            Map.of(new PriceKey("A", day(3)), d("15"), new PriceKey("A", day(4)), d("12")));
    var period = ValuationEngine.period(timeline.first(), timeline.last());
    assertThat(period.startingValue()).isEqualByComparingTo("1050");
    assertThat(period.change()).isEqualByComparingTo("-18");
    assertThat(period.returnPercent()).isEqualByComparingTo("-1.7143");
    assertThat(timeline.last().returnPercent()).isEqualByComparingTo("3.2");
  }

  @Test
  void incompleteEndpointsDoNotProduceInventedPeriodReturns() {
    var timeline =
        ValuationEngine.timeline(
            d("1000"), day(3), day(4), scenario(), Map.of(new PriceKey("A", day(4)), d("12")));
    var period = ValuationEngine.period(timeline.first(), timeline.last());
    assertThat(period.status()).isEqualTo("MISSING_ENDPOINT_PRICE");
    assertThat(period.change()).isNull();
    assertThat(period.returnPercent()).isNull();
  }

  @Test
  void zeroDenominatorsRemainUndefined() {
    var events = List.of(event(2, "A", "0.000001", "-0.01", "0.01", "0"));
    var timeline =
        ValuationEngine.timeline(
            d("0.01"),
            day(3),
            day(4),
            events,
            Map.of(new PriceKey("A", day(3)), d("1"), new PriceKey("A", day(4)), d("20000")));
    var period = ValuationEngine.period(timeline.first(), timeline.last());
    assertThat(period.startingValue()).isZero();
    assertThat(period.change()).isEqualByComparingTo("0.02");
    assertThat(period.returnPercent()).isNull();
    assertThat(period.status()).isEqualTo("ZERO_START_VALUE");
    assertThat(ValuationEngine.percent(d("1"), d("0"))).isNull();
  }

  @Test
  void inconsistentAccountingIsRejectedRatherThanDisplayed() {
    assertThatThrownBy(() -> value(4, List.of(event(2, "A", "-1", "10", "-10", "0")), Map.of()))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () ->
                value(
                    4,
                    List.of(event(2, "A", "1", "-10", "9", "0")),
                    Map.of(new PriceKey("A", day(4)), d("10"))))
        .isInstanceOf(DomainException.class);
  }
}
