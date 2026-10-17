# Phase reviews and interview preparation

## Phase 0 — architecture and delivery foundation

**Built:** a modular architecture, schema/invariants, API/financial policies, phased acceptance criteria and a reproducible repository layout. The existing Git checkout was preserved. See PLAN.md and the root architecture/data/decision documents.

**How it works:** React sends same-origin requests to Spring MVC. Authentication resolves the user; services own financial transactions; repositories persist aggregates/history in PostgreSQL. Pure decimal/FIFO calculations are independently testable. Modules deploy together so financial state can commit atomically.

**Important decisions:** one application/database, USD only, deterministic fictional prices, explicit historical clock, portfolio locking, FIFO and immutable records. These choices make failure behavior explainable without adding distributed-system machinery.

**Validation:** inspected the initially minimal repository and toolchain, then exercised the selected architecture in Phase 1. Phase 0 itself has no executable application/test suite; documentation is not a passing test claim.

**Incomplete:** valuation/performance, statistical risk, production operational hardening and deployment. Each later phase has separate acceptance criteria. Phase 0 is planning, not evidence that all planned features exist.

**Learn:** database ACID/isolation, Spring dependency injection/transaction proxies, decimal precision, threat-model basics, domain boundaries, and the difference between an order's modeled date and system recording time.

### Five interview questions

1. **Why not microservices?** Financial writes currently need one transaction across cash, orders, lots and history. A modular application meets that need with fewer failure modes; services could be separated later only if independent scaling/ownership justified it.
2. **Where should business rules live?** Controllers validate HTTP shape and resolve identity. Services enforce ownership, chronology and transaction orchestration. Pure functions compute settlement/FIFO; repositories handle SQL/entity persistence. PostgreSQL constraints protect basic storage invariants.
3. **Why PostgreSQL instead of an in-memory test database?** Locking, numeric behavior, partial indexes and append-only triggers are part of correctness. PostgreSQL-backed tests verify those actual mechanisms; H2 could hide differences.
4. **Why distinguish executedAt and recordedAt?** A simulated trade uses a known fictional historical close, while the request is processed now. Mixing those clocks would misrepresent execution and break later time-series assumptions.
5. **What makes an acceptance criterion useful?** It describes observable behavior and a check that could fail, such as two competing purchases being unable to overspend one balance. “Uses Spring” alone does not establish a working financial workflow.

## Phase 1 — working financial vertical slice

**Built:** account registration/login/logout; portfolio creation with initial USD funding; 3 fictional assets and 3,129 reproducible weekday price observations; exact-date simulated buys/sells; FIFO cost basis and realized sell P/L; holdings, paginated execution/ledger/audit history; responsive dark dashboard; Flyway, OpenAPI, structured logs, container definitions and CI workflow.

**Architecture in action:** every trade locks its owned portfolio, checks idempotency, obtains the local historical close, validates available resources, and commits order/lots/cash/ledger/audit together. A database error at the final audit insert rolls back earlier writes. The client shows the stored records and does no accounting arithmetic. The remaining purchase cost is labeled as cost basis, never market value.

**Important decisions:** reject missing prices instead of substituting zero; preserve cents through partial FIFO consumption; acquire the lock before replay lookup; return 404 for foreign portfolios; use HttpOnly session cookies with CSRF and ID rotation. Runtime sessions and retained database/filesystem state have different lifecycles.

**Tests executed:** the final local Maven verification ran 25 tests: 6 unit tests and 19 PostgreSQL integration tests, with zero failures/errors/skips. The production frontend build/type check passed. Four Playwright checks passed against the actual Spring/PostgreSQL/React stack on desktop and mobile Chromium. See TESTING.md for commands, exact scope and container/CI limitations. A present CI workflow is not a claim that GitHub Actions has executed it.

**Incomplete:** historical valuation, unrealized P/L, return/performance charts, allocation/risk statistics, stress tests, external data and public deployment. No Phase 2 work has begun. Authentication abuse controls, password recovery, durable client pending-order recovery, richer observability and broader accessibility testing are later work. Current browser accessibility checks cover labels/roles and viewport overflow; they are not an independent accessibility audit.

**Learn:** `BigDecimal` scale versus precision, HALF_EVEN rounding, FIFO lot accounting, SQL row locks under READ COMMITTED, transaction rollback across JPA/JDBC, idempotency under lost responses, session fixation/CSRF defenses, and the distinction between unit, integration and browser tests. Read FinancialMath.java, TradingService.java and PlatformIntegrationTest.java in that order.

### Five interview questions

1. **How do you prevent concurrent orders from spending the same cash?** `SELECT FOR UPDATE` locks the portfolio row before reading/replaying an order or mutating lots. At READ COMMITTED a waiting transaction then sees committed cash from the first one. Two USD 800 purchases against USD 1,000 produce one success and one insufficient-cash rejection; PostgreSQL integration tests exercise this race.
2. **Why is a unique idempotency key alone insufficient?** A unique index can reject duplicates, but without coordination the business operation may race before that check. Locking first lets the second request find/replay the committed execution. A different payload with the same key is a conflict. A network timeout does not prove rollback, so clients must reuse the key.
3. **How do partial FIFO sales preserve money?** Allocate remaining cost proportionally to the consumed quantity, round to cents HALF_EVEN, and subtract it from the lot. The final consumption takes the entire remainder. For a three-unit USD 1 cost lot, allocations of 0.33, 0.34 and 0.33 sum exactly to 1.00. Realized sell P/L is settled proceeds minus allocated cost.
4. **What happens if the audit insert fails after the order insert?** Both are in one Spring-managed transaction, sharing the same PostgreSQL connection/transaction through JPA and JdbcTemplate. The exception escapes and rolls everything back. A fault-injection test checks cash, holdings, execution, ledger and audit remain unchanged, then successfully retries the same key.
5. **Are HttpOnly cookies sufficient for secure authentication?** No. They reduce script access to the session ID, but browsers attach cookies automatically, so CSRF protection and SameSite are necessary. Rotate session IDs at login, invalidate at logout, use HTTPS/Secure cookies for deployment, check ownership server-side, hash passwords and add abuse controls. Phase 1 does not claim a complete public-production security posture.
