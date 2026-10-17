package com.aegis;

import static org.assertj.core.api.Assertions.*;

import com.aegis.market.SyntheticPrices;
import java.time.*;
import org.junit.jupiter.api.Test;

class SyntheticPricesTest {
  @Test
  void datasetIsReproduciblePositiveAndWeekdayOnly() {
    var prices = SyntheticPrices.generate();
    assertThat(prices).isEqualTo(SyntheticPrices.generate());
    assertThat(prices).hasSize(3129);
    assertThat(prices.stream().map(SyntheticPrices.Price::symbol).distinct())
        .containsExactly("ALTA", "BRIM", "COVE");
    assertThat(prices)
        .allSatisfy(
            p -> {
              assertThat(p.close()).isPositive().hasScaleOf(4);
              assertThat(p.date().getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
              assertThat(p.date()).isBetween(SyntheticPrices.START, SyntheticPrices.END);
            });
    assertThat(
            prices.stream().filter(p -> p.symbol().equals("ALTA")).findFirst().orElseThrow().date())
        .isEqualTo(LocalDate.of(2022, 1, 3));
  }
}
