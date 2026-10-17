# Phase 3 risk methodology — specification before implementation

Specification established before Phase 3 calculation code. Educational historical analysis of deterministic synthetic prices and simulated portfolios; neither a forecast nor an institution-grade model. USD, long-only, no borrowing, dividends, interest, fees, liquidity or execution effects. Phase 1 settlement and Phase 2 valuation remain authoritative.

## Two distinct questions

**Current-exposure historical risk:** reconstruct quantities, cash and exact-date marked position values at selected end-of-day date D, excluding trades after D. Apply past asset returns to those fixed marked exposures. Each observation independently starts at the same exposures; no compounding or historical strategy reconstruction. Cash has zero price exposure. The distribution is hypothetical performance of today's selected exposures under past shocks, not realized strategy returns.

**Historical portfolio drawdown:** use Phase 2's reconstructed end-of-day equity, including trades effective on each historical date. Never use current holdings to reconstruct this history. Both analyses end at D but answer different questions.

## Dates, samples, gaps and limits

- Supported dataset: 2022-01-03 through 2025-12-31. Weekdays are trading days; synthetic data has no exchange holidays. Weekends are excluded from return/drawdown observation calendars, never zero shocks. A weekend valuation of risky holdings remains unavailable because exact-date closes are absent.
- `lookbackDays` is 2–1461 inclusive **calendar days**, including D. Start = max(dataset/portfolio simulation start, D − lookbackDays + 1); disclose actual start. No observations before start and no preceding-price query outside it. First weekday is a baseline, not a return. Thus 365 calendar days do not mean 365 return observations.
- A return at weekday t requires positive persisted closes for every currently held asset at t AND the immediately preceding weekday in the selected range (Friday → Monday is consecutive). Never bridge a missing weekday, interpolate, carry forward or substitute zero. Dates align by calendar, not by intersecting whatever data survived.
- Report missing symbol/date pairs, candidate pair count, valid pair count and missing pair count. Any missing required historical close makes all sample-based metrics unavailable (`MISSING_HISTORY`), even if enough remaining pairs exist. Valid pairs may still be inspected, explicitly labeled incomplete. An unpriced selected-date exposure gives `MISSING_PRICES` and no partial aggregate.
- At least 30 valid daily observations for volatility, 100 for VaR95 and 500 for VaR99; otherwise metric value = null, status `INSUFFICIENT_OBSERVATIONS`, with requirement/count. Not zero. Counts are common aligned observations, including per-asset volatility.
- Positive-equity cash-only portfolios have analytically zero market-price volatility and VaR (`CASH_ONLY`, minimum-sample exception by proof, not fabricated observations); return distribution is empty. Zero equity yields `ZERO_EQUITY` for percentage/sample metrics. Stress USD changes may still be zero; percent is null. Inconsistent negative balances fail through existing accounting checks.
- Reuse Phase 2 bounds: 10,000 executions through D, 100 traded symbols; at most 1461 calendar dates × 100 prices, 1044 weekday points, 1043 return pairs and 146100 missing-price entries. No unbounded data queries. Maximum 8 scenarios/request, 100 overrides/scenario, names 1–60 characters. No new infrastructure.

## Decimal and statistical conventions

Current exposure E_i = Phase 2 round(quantity_i × close_i(D), USD cents, HALF_EVEN). Equity V = cash + Σ E_i. Fixed weight w_i = E_i / V; cash weight = cash / V. Allocation is percent of **total equity**, not just risky assets. Concentration = Σ (E_i / V)^2, excluding cash, with range [0,1]; cash-only = 0. This is exposure concentration, not a variance contribution or a diversification guarantee.

Historical simple asset return r_i,t = close_i(t) / close_i(previous weekday) − 1. Decimal division uses scale 18 HALF_EVEN. Shock P/L_t = Σ E_i × r_i,t, without cent rounding between assets. Portfolio shock return R_t = P/L_t / V (scale 18 HALF_EVEN); one-day loss L_t = −P/L_t. Cash contributes zero. This deliberately uses fixed **rounded marked exposures**; it may differ by cents from separately rounding hypothetical repriced quantities. Stress reprices quantities using the convention below.

Balances/exposures/P&L/percentiles use BigDecimal. Only standard deviation converts dimensionless returns to double for mean, squared deviations and square root. Sample s = sqrt(Σ(R_t − mean)^2 / (n−1)). Annualized s = s × sqrt(252); no drift adjustment. Output volatility in percentage points, scale 8 HALF_EVEN. Numerical tests allow absolute 1e−10 in fractional standard deviations, 1e−7 percentage points for reported volatility. IEEE 754 has finite precision; this is a statistical approximation, not accounting arithmetic. Daily asset volatility uses the same sample and formula.

VaR confidence c uses ascending decimal losses and **nearest rank** k = ceil(c × n), one-based. VaR = max(0, sortedLoss[k−1]); never interpolate, average tail losses, or take an absolute value of a gain. USD output rounded cents HALF_EVEN; percentage = 100 × unrounded VaR / V, scale 4 HALF_EVEN. A zero floor means all historical shocks gained; it does not guarantee no future loss. VaR99 ≥ VaR95 when both are available. Historical VaR is not maximum loss: losses can exceed it, unseen shocks are absent, and 500 observations still give only about five observations in the 1% tail. No probabilistic confidence interval is claimed.

## Drawdown

Inspect every eligible weekday in the actual Phase 2 equity series in the selected window. Any incomplete weekday makes the **whole window** unavailable (`MISSING_VALUATIONS`); omit weekends only, disclose missing dates. Require two complete weekday equity observations. Never connect around gaps. Peak P_t = max(E_0,…,E_t); decline d_t = (P_t − E_t) / P_t. Maximum drawdown = 100 × max(d_t), positive percentage, scale 4 HALF_EVEN. Compute comparisons at scale 18; retain earliest peak and earliest trough on ties. For zero drawdown, peak/trough are the first observation. If a running peak is nonpositive, result unavailable (`ZERO_EQUITY`). Report peak date, trough date, eligible observation count, missing dates and actual window. Cash-only, flat and rising histories have zero drawdown. Selected-window peaks do not include dates before its start.

## Non-mutating stress laboratory

Each scenario declares a common shock in percentage points and optional held-symbol overrides. Shocks must be decimal numbers in [−100, +100], at most 4 decimals; −100 permits a zero resulting price, below −100/negative prices rejected. Reject unknown/unheld overrides, nulls and excessive inputs. Price'_i = close_i(D) × (1 + shock_i/100), without intermediate price rounding. Value'_i = round(quantity_i × price'_i, cents HALF_EVEN). Contribution_i = value'_i − E_i. Stressed equity = unchanged cash + Σ value'_i; signed gain/loss = stressed equity − V; percent = 100 × gain/loss / V. Zero shock must exactly preserve the current value. No executions, lots, cash, ledger or audit writes; POST is computation, still authenticated/CSRF protected.

Illustrative presets (not historical episodes or forecasts): **Broad selloff −10%** for every risky asset; **Severe selloff −25%** for every risky asset; **Relief rally +10%** for every risky asset. **Custom** begins at 0%, with common slider and per-asset overrides. Multiple user snapshots can be compared with presets (8 maximum). Every result returns effective per-asset shocks and contributions; assumptions stay visible.

## Hand-verifiable fixtures (defined before code)

1. Fractional returns [0.01, 0.02, 0.03]: mean .02, sample SD .01; annual SD .01×sqrt(252) = .15874507866387544. Repeat pattern ten times for 30 observations: SD = sqrt(.002/29) = .008304547985373997. Annual volatility in percent = 13.183061232061236.
2. Portfolio cash 500, A exposure 300, B exposure 200, V=1000. A +10%, B −5% → P/L +20, portfolio return +2%; weights .3/.2/.5; concentration .13. Use Friday 2024-01-05 prices A100/B100, Monday Jan8 A110/B95. A missing Jan9 invalidates Jan8→9 and Jan9→10 for all assets even if Jan10 exists. Monday does not require weekend prices.
3. Decimal ascending loss fixture 1,…,100: nearest-rank 95% selects 95. Losses 1,…,500: 95% selects 475, 99% selects 495. For 99 observations VaR95 unavailable; for 499 VaR99 unavailable. All negative losses → VaR zero.
4. Actual weekday equities [100,120,90,110,80,130]: maximum decline (120−80)/120 = 33.3333%, peak second date, trough fifth date, despite final recovery. Flat [100,100] and rising [100,110] → zero; declining [100,80] → 20%. A missing intermediate weekday → unavailable.
5. Stress: cash 500; A quantity3, price100; B quantity10, price20. Common −10%, B override +20% → A270 (−30), B240 (+40), equity1010, gain10 / +1%. Common zero with no overrides → equity1000 exactly; −100% all → equity500; <−100 rejected. Cash-only equity500 remains500 for every valid scenario.
6. No future leakage: append a sale/price after requested D; earlier quantities, exposure, shock distribution and equity/drawdown remain byte-for-byte numerically unchanged. PostgreSQL tests verify owner/foreign/anonymous access, CSRF, gaps, bounds and immutable history counts/content across stress calls. Browser journeys use real APIs on desktop/mobile and validate unavailable/retry states.

## API/complexity contract

Owner-scoped `/api/portfolios/{id}/risk`: GET `/summary`, `/returns`, `/drawdown` with `date` (default latest dataset date) and `lookbackDays` (default 730); POST `/stress` with date and scenarios. Summary includes valuation, exposures, volatility, VaR, drawdown and full bounded return/gap data for a consistent dashboard snapshot. Dedicated GETs allow reuse. Ownership is checked before domain input handling; foreign IDs return 404. Existing session, CSRF, decimal JSON, domain error handling and read-only REPEATABLE READ transaction/15s timeout apply.

Load funding, bounded executions and one bulk price set; reuse the Phase 2 pure valuation engine once for the window. O(T log T + D×A + N log N) time, O(T + D×A + N) space, T≤10000, D≤1461, A≤100. No database query per asset, point or scenario. Sorting losses twice for two confidence levels remains bounded. Stress O(scenarios×held assets) after one dated valuation load. No schema migration or mutable risk cache is required. Testing must record actual execution results and environment limits; no production-readiness claim.
