package com.aegis.market;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Exact-date close. Missing observations fail explicitly; no carry-forward execution prices. */
public interface HistoricalPriceProvider {
  BigDecimal exactClose(String symbol, LocalDate date);
}
