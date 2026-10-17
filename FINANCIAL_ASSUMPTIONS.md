# Financial assumptions — Phase 1

AEGIS is educational simulation software, not a brokerage or investment adviser. All default assets and prices are fictional and clearly labeled. No exchange, bank or brokerage integration exists. No results are institution-grade risk forecasts.

## Units, precision, rounding

USD is the only currency. Monetary calculations use Java `BigDecimal` constructed from decimal values, never binary floating-point balances. API amounts/quantities are decimal strings. Browser number conversion is used only for formatted display, never for accounting decisions.

| Value | Precision / policy |
| --- | --- |
| Cash, funding, settled gross, cost basis, realized P/L | USD cents, HALF_EVEN when rounding is necessary |
| Historical closing price | 4 decimal places |
| Quantity | Up to 6 decimal places; `(0, 1,000,000]` per order |
| Initial funding | `[0.01, 1,000,000,000.00]` USD; excess precision rejected |
| Cash storage | PostgreSQL `numeric(19,2)`; service rejects overflow |

An order's settled gross is `round_half_even(quantity × exact_close, 2)`. A zero rounded gross is rejected. Price and quantity inputs with excess precision are rejected rather than silently truncated. Fees are zero. BUY deducts gross; SELL adds gross.

## Historical simulation and missing prices

The client explicitly selects an execution date. The exact stored closing price for that asset/date is required. Unknown/missing prices are `null` in quote responses and a `PRICE_UNAVAILABLE` rejection on execution, never zero or an automatic forward-fill. Default data spans 2022-01-03 to 2025-12-31 on weekdays. Exchange holidays are not modeled.

The synthetic reference close timestamp is **21:00 UTC** on that date. This is an educational convention, not a real exchange session, timezone/DST model, order arrival time or execution guarantee. `executedAt` is this modeled historical timestamp; `recordedAt` is the actual server receipt-processing timestamp. No claim of real historical exchange execution is made.

Orders for a portfolio must progress in nondecreasing historical date order. Same-date orders execute sequentially under the portfolio lock. Retrying a stored idempotency key can replay an earlier order without re-execution. Initial funding is treated as available before the first simulated order; its real recording date is not a historical cash-flow observation. Phase 2 must define dated funding/return conventions before using these records in a time series. Knowing future synthetic prices is permitted: this is accounting education, not a predictive backtest or investment strategy evaluation.

## FIFO cost basis and realized profit/loss

A buy opens a lot with original quantity and cost equal to settled gross. A sell consumes oldest remaining lots first.

For a partial consumption of `q` from a lot with remaining quantity `Q` and cost `C`:

`allocated_cost = round_half_even(C × q / Q, 2)`

A full consumption takes **all** remaining cost, preserving rounding remainders. The lot retains `Q − q` units and `C − allocated_cost` USD. Then:

`realized_P/L = settled_sell_gross − sum(allocated_cost)`

Example used in tests: buy 10 units at USD 10 and 5 at USD 20; sell 12 at USD 30. Proceeds = USD 360; FIFO cost = 100 + 40 = USD 140; realized P/L = USD 220; remaining = 3 units with USD 60 cost. Cash starting at USD 1,000 becomes USD 1,160. This example is a manually chosen test fixture, not a market observation.

A three-unit USD 1.00 cost lot sold one unit at a time allocates USD 0.33, 0.34, 0.33 under the remaining-lot policy. Total allocated cost remains USD 1.00. Sell records store realized P/L; remaining holdings show cost basis, not market value. Unrealized P/L and portfolio performance are not implemented in Phase 1.

## Synthetic data generation

`SyntheticPrices` uses per-asset fixed seeds 73129, 73130, 73131 for ALTA, BRIM, COVE. Dataset version: `synthetic-v1-seed-73129`. There are 1,043 weekday observations per asset, 3,129 total. Prices follow a simple multiplicative lognormal-style path with asset-specific volatility, a negative 2023 regime and positive 2022/2024/2025 drift; 2023 volatility is amplified. Prices have a positive floor. Variation is plausible for demonstration, not calibrated to real securities. Floating-point math in this generator describes fictional paths only; resulting closes are immediately rounded to four-decimal `BigDecimal` and persisted. Tests check deterministic output, range, positivity and count. Seed insertion is transactional, serialized by an advisory lock and idempotent. Existing rows are not overwritten on startup.

## Deliberate omissions

Dividends, splits, corporate actions, interest, taxes, fees, bid/ask spreads, slippage, liquidity, exchange calendars, settlement delays, leverage, shorts, order books, open/limit orders, FX, real-time updates and real market providers are absent. All accepted simulated orders fill immediately at the documented synthetic close. There are no pending order states.

Valuation time series, returns, volatility, drawdown, VaR, stress testing, allocation charts and correlation belong to later phases. Their mathematical conventions, sample requirements, missing-data treatment and numerical tests must be documented before implementation. Phase 1 exposes no placeholder risk figures.

`HistoricalPriceProvider` is the seam for later price sourcing. An external provider must first define licensing/redistribution rights, provenance, dataset/version timestamps, a persisted cache, missing/stale rules and failure behavior. Execution should use persisted verified observations, not depend on a live network call within a financial transaction. No paid key is required now.
