package com.aegis;

import static org.assertj.core.api.Assertions.*;

import com.aegis.shared.DomainException;
import com.aegis.trading.FinancialMath;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class FinancialMathTest {
  private static BigDecimal d(String x) {
    return new BigDecimal(x);
  }

  private static FinancialMath.Lot lot(String quantity, String cost) {
    return new FinancialMath.Lot(UUID.randomUUID(), d(quantity), d(cost));
  }

  @Test
  void settlesUsingHalfEvenCents() {
    assertThat(FinancialMath.gross(d("1"), d("1.0050"))).isEqualByComparingTo("1.00");
    assertThat(FinancialMath.gross(d("1"), d("1.0150"))).isEqualByComparingTo("1.02");
    assertThat(FinancialMath.gross(d("0.100001"), d("80.1234"))).isEqualByComparingTo("8.01");
  }

  @Test
  void rejectsZeroNegativeAndSubCentOrders() {
    for (String quantity : List.of("0", "-1", "0.000001")) {
      assertThatThrownBy(() -> FinancialMath.gross(d(quantity), d("1.00")))
          .isInstanceOf(DomainException.class);
    }
    assertThatThrownBy(() -> FinancialMath.gross(d("1"), d("0")))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void consumesOldestLotsBeforeNewerLots() {
    var result =
        FinancialMath.fifo(List.of(lot("10", "100.00"), lot("5", "100.00")), d("12"), d("360.00"));
    assertThat(result.consumptions()).hasSize(2);
    assertThat(result.consumptions().get(0).quantity()).isEqualByComparingTo("10");
    assertThat(result.consumptions().get(1).quantity()).isEqualByComparingTo("2");
    assertThat(result.totalCost()).isEqualByComparingTo("140.00");
    assertThat(result.realizedPnl()).isEqualByComparingTo("220.00");
  }

  @Test
  void partialSalesPreserveRoundingRemainderUntilFinalSale() {
    var first = FinancialMath.fifo(List.of(lot("3", "1.00")), d("1"), d("0.50"));
    assertThat(first.totalCost()).isEqualByComparingTo("0.33");
    var second = FinancialMath.fifo(List.of(lot("2", "0.67")), d("1"), d("0.50"));
    assertThat(second.totalCost()).isEqualByComparingTo("0.34");
    var third = FinancialMath.fifo(List.of(lot("1", "0.33")), d("1"), d("0.50"));
    assertThat(first.totalCost().add(second.totalCost()).add(third.totalCost()))
        .isEqualByComparingTo("1.00");
  }

  @Test
  void computesLossAndRejectsOverselling() {
    assertThat(FinancialMath.fifo(List.of(lot("2", "20.00")), d("2"), d("10.00")).realizedPnl())
        .isEqualByComparingTo("-10.00");
    assertThatThrownBy(() -> FinancialMath.fifo(List.of(lot("1", "10.00")), d("2"), d("20.00")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("more than you own");
    assertThatThrownBy(() -> FinancialMath.fifo(List.of(), d("1"), d("10.00")))
        .isInstanceOf(DomainException.class);
  }
}
