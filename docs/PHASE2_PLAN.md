# Phase 2 implementation and acceptance

Baseline: PR #1 merged into main at ce45a8e2d3e605b4f48f063deb4e9115fc160085. Work starts on feature/aegis-phase-2. No Phase 3 risk engine.

## Conventions decided before implementation

- Persist simulation_start_date = 2022-01-03 for existing and new portfolios. The single initial deposit is available at the opening of that date; created_at and ledger recorded_at retain their actual timestamps. This explicitly models a historical simulation, not account existence on that real date.
- Value at end-of-day using ALL immutable executions with price_date <= the selected date; exclude all future trades. Historical cost comes from original buy gross less immutable sell allocations, never current remaining lots. End-of-day aggregates do not depend on arbitrary UUID ordering of tied timestamps.
- Cash = initial funding + historical sell gross - historical buy gross. Mark each position at its exact date's stored close; round each position to USD cents HALF_EVEN and sum. No forward-fill, including weekends. Cash-only dates require no asset price.
- Since-start P/L = value - initial funding; simple return = P/L / initial funding * 100. Range change = ending EOD value - starting EOD value; range return divides by the starting EOD value. Deposits are never returns. This is neither TWR nor MWR. Zero starting value makes range percentage undefined.
- Default "current" = final supported synthetic date, 2025-12-31, not wall-clock today. Requests before simulation start or after the supported horizon fail explicitly.
- Missing prices leave aggregate market value/equity/unrealized P/L/returns null; cash and realized P/L remain known. Series retain missing dates with named missing symbols. Closed positions do not need a price.
- Bound requests to 1,461 inclusive calendar days, 10,000 executions, and 100 distinct traded assets. Bulk-read prices/events once and advance accounting state through dates; reject limits rather than truncate.

## Acceptance

1. Owner-scoped valuation, daily value-series, performance, marked holdings and allocation endpoints; date validation and bounded database queries.
2. Exact $1,000 / buy 10@$10 / sell 4@$15 / mark 6@$12 test: cash 960, market 72, equity 1032, remaining cost 60, realized 20, unrealized 12, total P/L 32, simple return 3.20%.
3. Tests for no trades, buys, partial/full sells, multiple assets, tied same-day records, missing prices/weekends, negative returns, invalid/bounded ranges, future-trade exclusion, zero denominators, existing V1 migration and resource ownership.
4. Real-data interactive equity chart, capital baseline, date selection, explicit gap/insufficient states and accessible data view; market-value allocation; holdings/P&L dashboard on desktop/mobile. No placeholder results.
5. All previous tests retained; backend, production frontend/type/format, PostgreSQL and browser suites executed. Preserve existing local data, update documents/mentoring, push branch and open PR without merging.
