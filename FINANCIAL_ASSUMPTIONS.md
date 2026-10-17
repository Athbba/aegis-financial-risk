# Financial assumptions — Phases 1 and 2

AEGIS is educational simulation software, not a brokerage or investment adviser. Default assets/prices are deterministic fiction. No real exchange execution, bank or brokerage integration exists. These calculations are not institution-grade forecasts.

## Units, settlement and rounding

USD only; no implicit USD/SAR conversion. Monetary/quantity arithmetic uses Java BigDecimal and PostgreSQL numeric. JSON decimals are strings. Browser Number conversion is only for formatting, chart coordinates and presentation, never financial calculations.

| Value | Policy |
| --- | --- |
| Cash, funding, trade gross, cost basis, marked position value, P/L | USD cents; HALF_EVEN |
| Synthetic close | 4 decimals |
| Quantity | At most 6 decimals; `(0, 1000000]` per order |
| Initial deposit | `[0.01, 1000000000.00]`; one per portfolio |
| Return/allocation percentages | Percentage points with 4 decimals, HALF_EVEN; UI displays 2 |

Trade gross = round(quantity × exact close, 2). Reject a rounded zero gross and excess input precision. BUY deducts gross; SELL adds gross. Fees are zero. Each **aggregate position's** marked value is rounded to cents before summing positions. This can differ by a cent from summing hypothetical individually rounded lot marks; aggregate position rounding is the documented convention. Cash and sell P/L use actual settled records, not recomputed unrounded price products.

## The simulation clock and capital baseline

**All existing and new portfolios begin at the opening of 2022-01-03**, the synthetic dataset's first date. The one initial funding amount is available then, even if the real account was created in 2026. This is a chosen historical-simulation baseline, not a claim that the account existed in the real past. V2 adds persisted `simulation_start_date`; it does not rewrite `created_at`, funding `recorded_at`, cash, orders, lots or audit history. Before the first trade, capital sits in cash.

Dates before that start fail with BEFORE_SIMULATION_START; there is no invented zero balance before inception. The supported horizon ends on 2025-12-31. An omitted valuation date means this latest **dataset** date, not today's wall-clock date or the last nonmissing quote for an asset. No deposit is counted as profit.

Snapshots are end-of-day after all executions with `price_date <= date`. A synthetic reference close is 21:00 UTC; it is not a real exchange session/DST model. `executedAt` is modeled time; `recordedAt` is actual processing time. Trades must progress in nondecreasing historical dates. Knowing later synthetic prices is allowed: this is accounting education, not a predictive strategy evaluation.

Valuations reconstruct the portfolio **as known from the stored records now**, at the requested historical date. They are not bitemporal “what the system knew at an earlier real recording timestamp” reports. Appending a permitted historical execution can change subsequent historical snapshots. Same-day executions all belong to that day's closing snapshot; no intraday valuation API exists.

## Historical accounting and valuation

For historical date d, using the immutable execution records and FIFO sell allocations through d:

- cash(d) = initial deposit − sum(BUY gross) + sum(SELL gross)
- quantity(asset,d) = sum(BUY quantity) − sum(SELL quantity)
- remaining cost(asset,d) = sum(BUY settled gross) − sum(allocated sell cost)
- realized P/L(asset,d) = sum(stored sell realized P/L)
- market value(asset,d) = round(quantity(asset,d) × exact dated close, 2)
- total value(d) = cash(d) + sum(position market values)
- unrealized P/L(asset,d) = market value(asset,d) − remaining cost(asset,d)
- total P/L(d) = realized P/L(d) + unrealized P/L(d) = total value(d) − initial deposit

Present-day `position_lot.remaining_quantity` and `remaining_cost` are **never** used to reconstruct old positions. Buy gross and immutable sell allocations preserve historical cost even after later partial/full sales. End-of-day sums are independent of arbitrary UUID ordering when recording timestamps tie. Execution-time FIFO was already fixed by acquisition time and generated lot sequence in Phase 1.

Closed positions have zero quantity, cost and market value, retain realized P/L, and need no close. The marked-positions API includes closed positions to preserve individual asset performance. A zero remaining cost makes the unrealized return percentage undefined, not infinite or zero. Missing funding or inconsistent accounting fails explicitly rather than silently repairing financial state.

### FIFO policy retained from Phase 1

A buy opens a lot at settled gross cost. Partial consumption q from remaining quantity Q and cost C allocates `round(C × q / Q, 2)`. Final consumption takes all remaining cost. Remaining quantity/cost are reduced by the actual allocation. Realized sale P/L = settled proceeds − allocated costs. A 3-unit USD 1.00 lot consumed one unit at a time allocates 0.33, 0.34, 0.33, preserving the full dollar. The engine checks that P/L reconciles to equity minus capital.

## Simple performance conventions

With one initial deposit and **no subsequent deposits/withdrawals**, since-start return is:

`return_percent(d) = 100 × (total_value(d) − initial_capital) / initial_capital`

For user-selected inclusive endpoints a and b:

`period_change = total_value(b) − total_value(a)`

`period_return_percent = 100 × period_change / total_value(a)`

Both endpoints are end-of-day. The range return excludes changes before/within the first day's closing baseline; since-start return always compares against opening initial capital. A single-date complete range has zero endpoint change but insufficient observations for a trend chart. A zero starting value makes range percentage null with ZERO_START_VALUE; absolute change remains available. No annualization, benchmark, time weighting or money weighting is implemented or claimed. If cash flows are added later, this return convention must be revisited before use.

Individual positions expose realized, unrealized and total P/L. `unrealizedReturnPercent` divides unrealized P/L by the **remaining** cost basis, not cumulative portfolio funding. It is not a lifecycle/annualized asset return.

## Exact-date prices, weekends and gaps

Only the exact stored close for that symbol/date is used. No automatic carry-forward or interpolation, including weekends and weekday holes. Unknown is not zero. Cash-only dates are fully valued without price observations. On a date with any unpriced open asset:

- status = MISSING_PRICES, with the missing symbols;
- cash, initial capital, realized P/L, quantities and cost remain known;
- aggregate market value, total equity, unrealized/total P/L and return are null;
- individual priced positions may retain their known values, but no partial aggregate is presented as the portfolio total;
- allocation weights are unavailable, and a period with a missing endpoint has no change/return estimate.

The calendar-day series retains those dates as explicit null points. The chart breaks its line at gaps. The deposited-capital baseline is always identifiable as capital, never a return. Two complete observations are the minimum for the trend chart; even two are merely a comparison, not statistical inference. Intermediate gaps are disclosed even when both endpoints suffice for a simple period return.

Allocation includes cash and open assets: weight = 100 × component market value / total value. Zero total value produces ZERO_TOTAL_VALUE and undefined weights. Individually rounded weights need not sum to exactly 100%. This is composition, not a Phase 3 risk score.

## Required numerical example (test fixtures only)

| Date/event | Cash | Quantity | Cost | Marked value | Equity |
| --- | --- | --- | --- | --- | --- |
| Initial capital | 1000.00 | 0 | 0.00 | 0.00 | 1000.00 |
| 2024-01-02: buy 10 at 10.0000 | 900.00 | 10 | 100.00 | 100.00 | 1000.00 |
| 2024-01-03: sell 4 at 15.0000 | 960.00 | 6 | 60.00 | 90.00 | 1050.00 |
| 2024-01-04: close 12.0000 | 960.00 | 6 | 60.00 | 72.00 | 1032.00 |

Realized P/L = 60 − 40 = 20; unrealized P/L = 72 − 60 = 12; total P/L = 32; since-start return = 3.2000%. Jan 3–4 endpoint change = −18, return = −1.7143%. Both pure unit tests and PostgreSQL service tests execute this scenario. These hand-selected fictional fixture prices are not injected into the application's demo market.

## Dataset, computation limits and omissions

Default ALTA/BRIM/COVE data: version `synthetic-v1-seed-73129`, seeds 73129–73131, 1,043 weekday observations each (3,129 total), 2022-01-03 through 2025-12-31. A simple lognormal-style generator models asset-specific volatility, a negative/high-variance 2023 regime and positive drift in other years, with a positive floor. Floating-point is used only to generate fictional paths; stored closes are immediately rounded to four-decimal BigDecimal. Prices are not fitted to real assets and exchange holidays are not modeled. Startup insertion is transactional, advisory-lock serialized, repeatable and does not overwrite existing observations.

Analytics is bounded to 1,461 inclusive calendar days, 10,000 executions through the end date, and 100 distinct traded assets. Limits are rejected, not silently truncated. Bulk queries plus one chronological accounting pass avoid a database query per chart point. No valuation cache or precomputed snapshots are used, avoiding stale results after trades.

Dividends, splits, corporate actions, interest, taxes, fees, spreads, slippage, liquidity, settlement delays, leverage, shorts, limit/open orders, FX and external market providers remain absent. All accepted simulated orders fill immediately at the documented close. Phase 3 volatility, drawdown, VaR, correlation and stress tests are not implemented. No institution-grade risk forecasts are claimed. The existing HistoricalPriceProvider remains the seam for later licensed data with documented provenance/caching/failure rules; no paid API is required now.
