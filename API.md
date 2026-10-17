# AEGIS API — Phases 1 and 2

Base path `/api`, JSON requests. Exact decimal fields are **strings** in responses; use strings in requests to avoid binary conversion in clients. OpenAPI JSON is generated at `/v3/api-docs` on backend port 8080 and requires a signed-in session. It documents schemas/paths; these notes define the accounting and retry semantics. There is no trading-provider connectivity.

## Authentication and CSRF

1. `GET /api/auth/csrf` with a cookie jar. Retain cookies and the returned `token`.
2. Send `X-XSRF-TOKEN: <token>` with every POST, including register/login/logout. JSON bodies use `Content-Type: application/json`.
3. Register/login returns `{ "id": "uuid", "email": "normalized@example.test" }` and creates an HttpOnly `JSESSIONID`. Retain it for subsequent requests.
4. Fetch a fresh CSRF token after authentication (old token is cleared) and after logout before another login. CSRF tokens are not authentication tokens.

| Method/path | Access | Behavior |
| --- | --- | --- |
| GET `/auth/csrf` | Public | `{ "token": "..." }`; cookie-backed CSRF token |
| POST `/auth/register` | Public + CSRF | `{ "email": "...", "password": "..." }`; 201, registers and signs in |
| POST `/auth/login` | Public + CSRF | Same body; 200, session established |
| GET `/auth/me` | Session | Current user identity |
| POST `/auth/logout` | CSRF | 204, invalidates any current session and clears cookies |

Email is normalized with strip + lowercase, unique, maximum 254 characters. Password is 12–72 characters and at most 72 UTF-8 bytes. BCrypt cost 12. No login throttling/recovery/email verification yet; local development only.

## Portfolios, prices and trades

| Method/path | Behavior |
| --- | --- |
| GET `/portfolios?page=0&size=20` | Owner's paginated portfolios, newest first |
| POST `/portfolios` | `{ "name": "Core conviction", "initialCash": "100000.00" }`; 201, atomic initial funding |
| GET `/portfolios/{id}` | `{ "portfolio": {...}, "holdings": [{ "symbol": "ALTA", "quantity": "6.000000", "costBasis": "..." }] }` |
| GET `/assets?date=2024-01-02` | All fictional assets and exact-date closes; missing `closePrice` is null. Default date 2025-12-31 |
| POST `/portfolios/{id}/orders` | Simulate immediate BUY/SELL, 200; requires `Idempotency-Key` header |
| GET `/portfolios/{id}/orders?page=0&size=20` | Paginated executed orders, newest recording time first |
| GET `/portfolios/{id}/ledger?page=0&size=20` | Paginated signed cash movements and balance after |
| GET `/portfolios/{id}/audit?page=0&size=20` | Paginated funding/trade audit events |

All these routes require a session; POST also requires CSRF. Foreign/nonexistent portfolios return the same 404. Pages have `{ "items": [...], "page": 0, "size": 20, "total": 2 }`. Size range 1–100, page range 0–1,000,000. Sort uses recording/creation time descending then UUID descending. Large offsets are bounded but keyset pagination is a possible future improvement.

Portfolio names must be nonblank, at most 80 characters. Funding has at most two decimals and is in `[0.01, 1000000000]` USD. No currency selector, delete route, second funding, or withdrawal route exists.

Example order body:

```json
{
  "symbol": "ALTA",
  "side": "BUY",
  "quantity": "10.000000",
  "priceDate": "2024-01-02"
}
```

Use a unique key (e.g. UUID) for each intended order, 8–80 ASCII letters/digits/hyphens/underscores. The key's scope is the portfolio. Reuse **the same key and payload** after uncertain responses; never invent a new key merely because a request timed out. The original execution is replayed without cash/lot/history changes. Numerically equivalent quantities such as `1` and `1.000000` are equivalent. Reuse with different symbol/side/quantity/date returns 409. Failed orders do not reserve keys.

The browser preserves a pending request key while its trade form remains mounted. It does not persist pending keys across reloads or portfolio switching. After an uncertain response, retry the unchanged form before leaving it; after a reload inspect transaction history before placing another order. A durable client-side pending-request journal is later reliability work.

Executions include `id`, `portfolioId`, `symbol`, `side`, `quantity`, `executionPrice`, `grossAmount`, `realizedPnl`, `priceDate`, `executedAt`, `recordedAt`, `idempotencyKey`. A buy's realized P/L is zero; sells use FIFO settled cost. Exact execution price has four decimals; totals/cost/P&L have two. `executedAt` is the fixed fictional 21:00 UTC historical close; `recordedAt` is actual processing time. See [financial assumptions](FINANCIAL_ASSUMPTIONS.md).

## Errors

Failures use `application/problem+json` with `type`, `title`, `status`, `detail`, `code` and usually `instance`. Spring Security filter failures may omit `instance`. Field validation returns a stable code and a safe general message; it does not echo passwords/request bodies.

| Status | Examples |
| --- | --- |
| 400 | `INVALID_INPUT`: malformed JSON, missing header, invalid quantity/decimal precision/date/enum, invalid page |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS` |
| 403 | `FORBIDDEN`: CSRF failure |
| 404 | `NOT_FOUND`: unknown asset/portfolio or foreign portfolio |
| 409 | `IDEMPOTENCY_CONFLICT`, `EMAIL_UNAVAILABLE`, database `CONFLICT` |
| 422 | `INSUFFICIENT_CASH`, `INSUFFICIENT_HOLDINGS`, `PRICE_UNAVAILABLE`, `DATE_OUT_OF_ORDER`, `ORDER_TOO_SMALL`, `BALANCE_LIMIT`, `PASSWORD_LENGTH` |
| 500 | `INTERNAL_ERROR`: unexpected failure, safe detail; inspect server logs |

A health request at `/actuator/health` is public and returns UP when the server/database health check succeeds. This is an operational check, not proof a financial transaction works; integration/browser tests provide that evidence.

## Phase 2 — valuation and performance

All endpoints below require the current session and the same owner-scoped portfolio authorization. Foreign/nonexistent portfolios return 404; anonymous requests return 401. They are read-only GETs; the Phase 1 session/CSRF and decimal-string conventions are unchanged. The portfolio DTO additionally exposes `simulationStartDate` (2022-01-03 for migrated and new portfolios).

| Path under `/api/portfolios/{id}` | Parameters | Response |
| --- | --- | --- |
| `/valuation` | optional `date` (default 2025-12-31) | Dated `Snapshot` |
| `/value-series` | required inclusive `from`, `to` | Daily calendar series with funding baseline, complete/missing day counts |
| `/performance` | required inclusive `from`, `to` | One consistent response containing ending snapshot, endpoint-period comparison, daily series and allocation |
| `/holdings` | optional `date` | Dated marked positions, including closed positions' realized P/L |
| `/allocation` | optional `date` | Cash/open-asset market values and percentage weights |

`Snapshot`: `date`, `status`, `initialCapital`, `cash`, `marketValue`, `totalValue`, `realizedPnl`, `unrealizedPnl`, `totalPnl`, `returnPercent`, `missingSymbols`, `positions`.

Each position: `symbol`, `quantity`, `costBasis`, `valuationPrice`, `marketValue`, `realizedPnl`, `unrealizedPnl`, `totalPnl`, `unrealizedReturnPercent`. Cost is historical FIFO remaining settled cost. `valuationPrice` is the exact requested close, never an implied real-time/latest-before price. It is null for a closed position because no mark is required.

`Series`: `from`, `to`, `simulationStartDate`, `currency`, `initialCapital`, `completeDays`, `missingDays`, `points`. Each point contains date/status, cash, market/equity, P/L/return and missing symbols. No price-gap dates are dropped. The current simulation horizon is fixed and documented; optional dates do not silently use wall-clock today.

`Performance`: `simulationStartDate`, `currency`, `summary`, `period`, `series`, `allocation`. `period` contains `startingValue`, `endingValue`, `change`, `returnPercent`, `status`. Since-start percentage in summary uses opening initial capital; period percentage uses starting end-of-day value. These are simple returns, not TWR/MWR. The combined endpoint supplies all dashboard figures from one REPEATABLE READ transaction.

`Allocation`: `date`, `status`, `missingSymbols`, `items` containing symbol (`CASH` for available money), market value and weight percentage. Cash is included in the denominator. Closed positions are omitted from allocation, not from performance history.

### Missing and insufficient data contracts

A valid date with any unpriced open position returns **200** with `status: MISSING_PRICES`. Known cash, realized P/L, quantity and cost are retained; aggregate market value/equity/unrealized P/L/total P/L/return are **null**, not zero. Allocation weights are null and its chart must be withheld. A cash-only date needs no asset prices. Weekends are ordinary calendar dates with missing closes for held assets.

A period with a missing endpoint has `status: MISSING_ENDPOINT_PRICE` and null change/return. A zero starting value has `status: ZERO_START_VALUE`, known change and null percentage. Allocation with zero total value has `status: ZERO_TOTAL_VALUE` and null weights. Two complete observations are required to draw the UI trend chart; a single day is still a valid API snapshot/series.

### Bounds and errors

Malformed/missing dates: 400 INVALID_INPUT. Reversed ranges, dates after 2025-12-31, or ranges over 1,461 inclusive calendar days: 422 INVALID_DATE_RANGE. A start before portfolio simulation start: 422 BEFORE_SIMULATION_START. More than 10,000 executions through the end date or 100 distinct traded assets: 422 ANALYTICS_LIMIT. No silent truncation/sampling. Missing funding: FUNDING_UNAVAILABLE; inconsistent execution/allocation/accounting records: ACCOUNTING_DATA_INCONSISTENT (422). Those integrity failures are explicit, not replaced with estimated values.

Historical quantities, cost and cash are reconstructed from immutable executions/funding/allocations, never current mutable lot remainders. Details/formulas and the manually verifiable 3.20% fixture are in FINANCIAL_ASSUMPTIONS.md. OpenAPI JSON includes the new DTOs and exact-decimal string schema.


## Phase 3 — financial risk (owner scoped)

All routes start at `/api/portfolios/{id}/risk`, require the existing authenticated session, and return 404 for another user's portfolio. Decimal fields are JSON strings. Read requests are transactionally consistent within each response; separate responses may see later committed trades. Existing trading and valuation endpoints are unchanged.

| Method / suffix | Input | Result |
| --- | --- | --- |
| GET `/summary` | `date=2025-12-31&lookbackDays=730` (defaults shown) | Actual clipped `from`, selected `date`, requested calendar window, USD valuation, `analysis` and actual-equity `drawdown` |
| GET `/returns` | Same date/window | Status, candidate/missing pair counts, missing symbol/date prices, valid shock points with decimal returnPercent, signed lossUsd and fractional assetReturns |
| GET `/drawdown` | Same date/window | Status, percent decline, peakDate, troughDate, complete weekday observation count, missingDates |
| POST `/stress` | JSON date and 1–8 scenarios; CSRF header required | Scenario array with unchanged cash, current/stressed equity, signed gainLoss/percent and effective per-asset shocks/prices/values/contributions |

`analysis` includes `dailyVolatility`, `annualVolatility`, `var95`, `var99`, `assets` (including CASH, equity weights and daily per-asset volatility), `concentration` (Σ risky equity weights²) and `returns`. Volatility metrics carry `status`, `value` (percentage points), `observations`, `minimum`; VaR carries `status`, `usd`, `percent`, `observations`, `minimum`. Missing/insufficient metrics have null values. Named states: COMPLETE, CASH_ONLY (analytic zero), ZERO_EQUITY, MISSING_PRICES, MISSING_HISTORY, INSUFFICIENT_OBSERVATIONS; drawdown additionally uses MISSING_VALUATIONS. Return status COMPLETE describes data alignment, while each metric independently enforces its minimum.

Lookback is 2–1461 inclusive **calendar** days, clipped at the portfolio simulation start, not an observation count. Dates must be in 2022-01-03–2025-12-31. Current unpriced holdings remain explicitly unavailable; missing historical weekdays invalidate estimates without discarding the diagnostics. Limits retain 100 traded symbols/10000 executions; violations return 422 with existing problem-detail conventions. A foreign owner is checked before domain validation. Malformed date/JSON formats return 400; invalid business ranges/scenarios return 422. CSRF failures return 403, unauthenticated requests 401.

Example request after obtaining the session and `X-XSRF-TOKEN` as above:

```http
POST /api/portfolios/{id}/risk/stress
Content-Type: application/json
X-XSRF-TOKEN: <session-csrf-token>

{
  "date": "2025-12-31",
  "scenarios": [
    {"name": "Broad selloff", "commonShockPercent": "-10", "overrides": {}},
    {"name": "Mixed", "commonShockPercent": "-10", "overrides": {"BRIM": "20"}}
  ]
}
```

Scenario names are 1–60 characters; shocks −100…+100 percentage points with ≤4 decimals; ≤100 overrides, each naming a held asset. Overrides replace the common shock, not add to it. Missing/null scenarios and inputs are rejected. Cash is never shocked. Result values reconcile at the aggregate-position cent rounding convention. A stress request never creates an order or financial/audit record. No idempotency key is needed for this read-only computation. See [the complete formulas](docs/PHASE3_RISK_SPEC.md).
