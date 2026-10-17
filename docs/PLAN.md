# Delivery plan and acceptance criteria

## Phase 0 — architecture and repository setup
Accepted when module boundaries, schema/invariants, financial assumptions, API contracts, local commands, and phased acceptance criteria are documented. Use the existing checkout; no worktree is necessary.

## Phase 1 — transactional vertical slice
- Java 21 / Spring Boot modular application, PostgreSQL with Flyway, React/TypeScript dashboard.
- Users register and sign in using hashed passwords and server sessions; logout invalidates the session. CSRF protection and ownership enforcement apply to financial endpoints.
- Create a USD portfolio with a positive, bounded initial simulated deposit. Funding creates a ledger entry and audit event atomically.
- Three explicitly fictional assets have repeatable weekday prices spanning 2022–2025, with a fixed seed and version.
- Execute buys and sells at an exact selected historical close, show price date/time and reject gaps, invalid quantities, insufficient funds, overselling, and dates before an earlier execution.
- A portfolio lock serializes financial changes; idempotency keys replay identical orders and reject key reuse with different payloads. FIFO lots preserve settled cost, sell allocations, and realized profit/loss.
- Read holdings, paginated execution/ledger/audit history and show usable loading, empty, validation, and failure states in a responsive dashboard.
- Execute domain tests, PostgreSQL integration tests (including concurrency and ownership), frontend build/type checks, and browser journeys against the real stack. Record actual outcomes.
- Containerized local startup, CI, OpenAPI and truthful project/mentoring documentation.

## Phase 2 — valuation and performance (not started)
Accept only with reproducible dated valuation, missing-price rules, return methodology, manual numerical test cases, performance/P&L charts, and API/browser tests.

## Phase 3 — risk (not started)
Accept only with documented sample requirements/formulas, deterministic tests for allocation, historical volatility, drawdown, 95/99% historical VaR, stress comparisons, explicit uncertainty and missing-data states.

## Phase 4 — reliability/security expansion (not started)
Accept with threat-model review, authentication abuse controls, expanded failure/concurrency tests, audit retention policy, operational metrics and documented recovery/backup drills.

## Phase 5 — presentation/deployment (not started)
Accept with visual/accessibility review, reproducible demos, deployment and rollback validation, screenshots, and final portfolio documentation. Phase 1 styling is functional groundwork; deployment is not claimed.
