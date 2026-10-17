# Phase 1 API

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
