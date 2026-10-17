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

## Phase 2 — valuation and performance
Detailed acceptance criteria and the convention chosen before implementation are in [PHASE2_PLAN.md](PHASE2_PLAN.md). Delivery covers immutable historical reconstruction, simple one-deposit returns, honest gaps, marked holdings/allocation, interactive charts, migration preservation and numerical/PostgreSQL/browser validation. Phase 3 builds on these interfaces.

## Phase 3 — risk (implemented; delivery evidence in TESTING.md)
Delivered historical volatility, drawdown, 95/99% nearest-rank historical VaR, exposure concentration (not variance attribution), stress comparisons and explicit uncertainty/missing-data states. Pre-code methodology and deterministic fixtures: [PHASE3_RISK_SPEC.md](PHASE3_RISK_SPEC.md). Phase 4 implementation and acceptance evidence are documented separately.

## Phase 4 — reliability/security expansion
See [PHASE4_PLAN.md](PHASE4_PLAN.md) for the pre-code audit, [PHASE4_SECURITY_REVIEW.md](PHASE4_SECURITY_REVIEW.md) for implemented controls and residual limits, and TESTING.md for executed evidence. Stop before Phase 5.

## Phase 5 — presentation/deployment (not started)
Accept with visual/accessibility review, reproducible demos, deployment and rollback validation, screenshots, and final portfolio documentation. Phase 1 styling is functional groundwork; deployment is not claimed.
