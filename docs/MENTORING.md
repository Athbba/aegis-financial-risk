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

**Incomplete at the Phase 1 boundary:** historical valuation, unrealized P/L, return/performance charts, allocation/risk statistics, stress tests, external data and public deployment. This records the Phase 1 boundary; Phase 2 is reviewed below. Authentication abuse controls, password recovery, durable client pending-order recovery, richer observability and broader accessibility testing are later work. Current browser accessibility checks cover labels/roles and viewport overflow; they are not an independent accessibility audit.

**Learn:** `BigDecimal` scale versus precision, HALF_EVEN rounding, FIFO lot accounting, SQL row locks under READ COMMITTED, transaction rollback across JPA/JDBC, idempotency under lost responses, session fixation/CSRF defenses, and the distinction between unit, integration and browser tests. Read FinancialMath.java, TradingService.java and PlatformIntegrationTest.java in that order.

### Five interview questions

1. **How do you prevent concurrent orders from spending the same cash?** `SELECT FOR UPDATE` locks the portfolio row before reading/replaying an order or mutating lots. At READ COMMITTED a waiting transaction then sees committed cash from the first one. Two USD 800 purchases against USD 1,000 produce one success and one insufficient-cash rejection; PostgreSQL integration tests exercise this race.
2. **Why is a unique idempotency key alone insufficient?** A unique index can reject duplicates, but without coordination the business operation may race before that check. Locking first lets the second request find/replay the committed execution. A different payload with the same key is a conflict. A network timeout does not prove rollback, so clients must reuse the key.
3. **How do partial FIFO sales preserve money?** Allocate remaining cost proportionally to the consumed quantity, round to cents HALF_EVEN, and subtract it from the lot. The final consumption takes the entire remainder. For a three-unit USD 1 cost lot, allocations of 0.33, 0.34 and 0.33 sum exactly to 1.00. Realized sell P/L is settled proceeds minus allocated cost.
4. **What happens if the audit insert fails after the order insert?** Both are in one Spring-managed transaction, sharing the same PostgreSQL connection/transaction through JPA and JdbcTemplate. The exception escapes and rolls everything back. A fault-injection test checks cash, holdings, execution, ledger and audit remain unchanged, then successfully retries the same key.
5. **Are HttpOnly cookies sufficient for secure authentication?** No. They reduce script access to the session ID, but browsers attach cookies automatically, so CSRF protection and SameSite are necessary. Rotate session IDs at login, invalidate at logout, use HTTPS/Secure cookies for deployment, check ownership server-side, hash passwords and add abuse controls. Phase 1 does not claim a complete public-production security posture.

## Phase 2 — historical valuation, performance and charts

**Built:** owner-scoped dated valuation, daily historical equity series, combined performance summary, marked positions and cash/asset allocation; realized/unrealized/total P/L; simple initial-capital and endpoint returns; interactive desktop/mobile charts, a capital baseline, accessible dated records, gaps and retry states. The original execution/authentication/history flows remain intact. No Phase 3 risk engine was added.

**How it works:** immutable BUY/SELL records supply historical quantity and signed cash. Immutable sell allocations supply consumed FIFO cost. The pure engine advances balances through effective dates, marks each open position with the exact requested close, rounds its value to USD cents and verifies total P/L equals equity minus capital. It never uses current remaining lots to infer an older portfolio. Read-only REPEATABLE READ keeps summary, series, positions and allocation in one coherent response. Bounded bulk database reads avoid one query per chart point.

**Important decisions:** initial capital is available at the opening of persisted 2022-01-03, even for accounts actually created later; V2 leaves original audit clocks and balances untouched. Since-start return divides gain by initial capital, whereas a selected period divides endpoint change by starting EOD wealth. These are not TWR/MWR. Exact-date missing prices yield null totals and explicit chart gaps; a closed/cash-only position needs no price. A zero denominator is undefined. Same-day closing sums are order-independent after execution-time FIFO allocations are fixed.

**Tests executed:** 55 backend tests passed (20 unit, 35 PostgreSQL integration; no failures/errors/skips). The required fixture produces cash 960, mark 72, equity 1032, remaining cost 60, realized 20, unrealized 12, total gain 32 and return 3.20%. Six desktop/mobile browser cases passed on the real packaged stack, including both original journeys and the new historical performance path. Strict TypeScript/production build, frontend formatting and npm audit passed. A V1 migration fixture and an independent restored-backup row comparison verified existing data preservation. See TESTING.md for exact scope and current delivery limitations.

**Demonstrated journey:** fund 1,000, buy 10 ALTA at the Jan 2, 2024 synthetic close, sell 4 at Jan 3 close and view Jan 1–8. The actual fixed-seed dataset yields 997.52 equity (−2.48 total P/L), which differs deliberately from the isolated hand-verifiable 1032 fixture. Selecting Jan 1 excludes future orders; Jan 6 shows missing quotes instead of invented zero/forward-filled values. The browser test compares all displayed financial results to backend responses and exercises chart hover, keyboard, data table and retry behavior.

**Incomplete at Phase 2 delivery (risk added in Phase 3 below):** correlation matrices and variance attribution; benchmarks/investment-return annualization; multi-flow or time-/money-weighted returns; licensed external data; public deployment and broad security/accessibility/load validation. The simple return policy must change before additional external cash flows are introduced. Operational hardening from later phases remains open. No automatic PR merge is authorized.

**Learn:** immutable accounting reconstruction versus mutable current state; effective dates versus recording clocks; BigDecimal rounding/reconciliation; SQL range/index patterns and repeatable-read isolation; missing-data semantics; denominators in financial returns; chart accessibility and request cancellation; additive migrations and data-preserving upgrades.

### Five interview questions

1. **Why can't current holdings value an earlier date?** Later trades have already changed current quantities/costs. Reconstruct signed quantities and cash from only executions through the historical date, and subtract only FIFO allocations effective by then. The tests show 10 units before a future partial sale even though the current book contains 6.
2. **How did you reconcile real account creation with historical trades?** Persist a synthetic start date of 2022-01-03 and treat the one deposit as opening capital there. Preserve actual creation/recording timestamps for auditability. This is an explicit simulation convention, not a claim about real ownership in 2022. Before-start requests fail instead of creating an artificial return from zero.
3. **What is the difference between your two return percentages?** Since-start return is `(equity − initial capital) / initial capital × 100`. Range return is `(ending EOD equity − starting EOD equity) / starting EOD equity × 100`. In the fixture, 1050 to 1032 is −1.7143% for that period, while 1032 against 1000 opening capital is +3.20%. Neither calculation is TWR/MWR, and a zero range denominator is undefined.
4. **How do missing prices affect the dashboard?** Keep known cash/realized P/L/quantity/cost, but make affected market/equity/unrealized/total P/L/return fields null and name the unpriced symbols. Don't total only priced positions as though complete. Preserve missing dates and break the chart line. Closed positions need no price; cash-only weekends are valid.
5. **How do you keep historical analytics reliable without microservices or a cache?** Use one owner-scoped REPEATABLE READ transaction, capped execution/date/asset inputs, indexed immutable-cost sums and a bulk price read. Advance accounting balances once through dates and reconcile P/L. No per-point queries or cache invalidation are needed at this size. Numerical, PostgreSQL, migration, preservation and browser tests validate different failure modes.


## Phase 3 — explainable historical risk

Volatility measures how spread out the daily hypothetical returns are: subtract their average, square the differences, sum, divide by n−1 and take the square root. Annual volatility multiplies that daily number by √252. It is a convention for expressing historical dispersion, not a guaranteed forecast.

Historical VaR asks what losses the selected holdings would have had under old daily asset moves. Cash stays fixed. Sort those losses and choose the documented percentile. A 99% VaR can be exceeded; unseen shocks and a small tail sample make any guarantee inappropriate. Actual historical drawdown asks a different question: how far did the simulated portfolio's reconstructed equity fall from a previous high, while its trades and holdings changed through time?

Missing prices matter because removing a bad day or treating it as zero can suppress measured risk. AEGIS requires adjacent weekdays for every held asset and makes estimates unavailable on gaps. Stress testing instead applies a user's explicitly chosen percentage price change to current quantities, keeps cash unchanged, and shows the contributions without executing trades.

### Five Phase 3 interview questions

16. **Why use sample standard deviation and √252?** Sample deviation divides squared deviations by n−1 because the sample mean was estimated from those observations. √252 expresses daily dispersion on an annual scale under a conventional variance-scaling assumption; it is not a claim of independent real returns or guaranteed future volatility. AEGIS requires 30 aligned observations.

17. **How exactly does nearest-rank historical VaR work?** Reconstruct selected-date marked exposures, apply each aligned historical daily shock, negate decimal P/L to get loss, sort ascending and select one-based ceil(confidence×n). For losses 1…500, VaR95 selects 475 and VaR99 selects 495. AEGIS floors negative loss quantiles at zero and requires 100/500 observations. USD and equity percentage are reported separately.

18. **Why is VaR not a maximum loss, and how is it different from maximum drawdown?** A quantile leaves a tail beyond the selected loss and includes no unseen scenarios. Even 500 observations give about five in the 1% tail. AEGIS VaR holds selected-date exposures fixed; drawdown follows actual simulated equity including historical trades, retaining the running peak and worst subsequent decline. Neither predicts a future ceiling.

19. **What goes wrong if you align prices by dropping every missing row?** You can silently bridge a missing weekday, turning a two-day move into a one-day observation, or remove stress days from the sample. AEGIS enumerates expected weekdays, checks both endpoints for all held assets and reports gaps. Friday-to-Monday is consecutive; Monday-to-Wednesday with missing Tuesday is not. Any required historical gap makes the sample estimates unavailable.

20. **How would you prove stress simulation is correct and cannot place trades?** Keep the engine pure: quantity×price×(1+shock/100), cents HALF_EVEN per aggregate position, cash unchanged, contributions sum to total. With cash500, A3×100, B10×20, shocks A−10%/B+20% give equity1010 and gain10/+1%. Zero shock preserves equity. The application service uses read-only transactions and no mutation calls; PostgreSQL tests compare complete orders, lots, allocations, portfolio, ledger and audit rows before/after accepted and rejected requests, and security tests cover ownership/CSRF.


## Phase 4 — secure and recoverable backend operations

A severity label is a starting point: verify the affected version and exact application conditions using primary advisories, then evaluate a supported patch path against the existing contract. Here a tested Boot 4 migration resolved the earlier Spring findings without changing financial formulas. Layered admission/payload controls limit expensive work; database ownership, immutable history and real rollback tests still protect money-like state.

### Five Phase 4 interview questions

21. **How do you investigate a Critical dependency finding without either panicking or ignoring it?** Check exact coordinates, official advisory ratings/preconditions, public versus enterprise fixes and the supported dependency matrix. GitHub rated the two MVC findings Critical while Spring rated them Low/Medium; the application lacked SSE fragments/XSLT views, but the vulnerable dependency still existed. A disposable Boot 4/Framework 7 prototype passed every original test, so the patch was adopted. No ignore rule was substituted for a fix.

22. **How do you design login throttling without permanently locking out a user?** Apply temporary client and normalized-account budgets, use atomic increments, give Retry-After, expire the window and reset the account counter on success. Use identical accounting for unknown accounts and never trust arbitrary forwarded headers. PostgreSQL counters survive restarts; shared NAT budgets and distributed attacks remain limitations. Account/password verification and a global abuse strategy are separate concerns.

23. **Why can a timeout after a trade require recovery rather than a new request?** The transaction may have committed before its response was lost. Keep the original idempotency key and semantic payload, look up the owned stored result and retry that key. A momentary 404 can race an in-flight commit. The browser journal helps across refresh/login, but the database lock, uniqueness and payload comparison provide correctness. Tests deliberately drop a committed response and recover one execution after a real server restart.

24. **Which signals would you use to debug a failed financial request safely?** Start with its server-generated request ID, bounded route/status/outcome logs, request latency and commit/replay/rejection/rollback counters. Check readiness/database availability and inspect the owner's immutable execution/ledger/audit records through authorized tools. Do not dump request bodies, cookies, raw exception messages or passwords. Completion logs are different from durable audit records, and metrics reset at restart.

25. **How do you prove a backup and least-privilege role are useful?** Restore the actual custom-format dump into a new disposable database, compare ordered financial row fingerprints and verify the source is unchanged; test refusal of active/existing targets. Run normal authentication and buy/partial-sell flows with the restricted role, then assert DDL/price/history mutation is denied. Separate migration credentials from the serving process. These tests do not establish offsite retention, PITR, recovery objectives or protection against a trusted database owner.
