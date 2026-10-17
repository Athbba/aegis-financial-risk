package com.aegis.market;

import java.math.*;
import java.time.*;
import java.util.*;

/** Reproducible educational data only. Never represents exchange observations. */
public final class SyntheticPrices {
  public static final String VERSION = "synthetic-v1-seed-73129";
  public static final LocalDate START = com.aegis.shared.SimulationCalendar.START;
  public static final LocalDate END = com.aegis.shared.SimulationCalendar.END;

  public record Asset(
      String symbol, String name, String sector, long seed, double initial, double volatility) {}

  public record Price(String symbol, LocalDate date, BigDecimal close) {}

  public static final List<Asset> ASSETS =
      List.of(
          new Asset("ALTA", "Alta Systems (fictional)", "Technology", 73129, 80, .018),
          new Asset("BRIM", "Brim Infrastructure (fictional)", "Infrastructure", 73130, 120, .009),
          new Asset("COVE", "Cove Energy (fictional)", "Energy", 73131, 55, .027));

  private SyntheticPrices() {}

  public static List<Price> generate() {
    List<Price> result = new ArrayList<>();
    for (Asset asset : ASSETS) {
      Random random = new Random(asset.seed());
      double value = asset.initial();
      for (LocalDate date = START; !date.isAfter(END); date = date.plusDays(1)) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY)
          continue;
        // Shared modeled regimes: growth, drawdown, recovery. Weekdays are not an exchange
        // calendar.
        double drift = date.getYear() == 2023 ? -.0011 : .00065;
        double volatility = asset.volatility() * (date.getYear() == 2023 ? 1.5 : 1);
        value = Math.max(1, value * StrictMath.exp(drift + volatility * random.nextGaussian()));
        result.add(
            new Price(
                asset.symbol(),
                date,
                BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_EVEN)));
      }
    }
    return List.copyOf(result);
  }
}
