# Progress

Status: **Phase 5 deployment preparation in progress; public release BLOCKED pending target host/domain/off-host storage/operator verification. No merge or launch.**

Phase 1 was merged by PR #1; Phase 2 was merged by PR #2 (`37dda5c`). Phase 3 started from updated main on `feature/aegis-phase-3`. Its methodology was committed before code (`6f02681`). PR #3 was subsequently merged as `7870135`; its main CI passed before Phase 4 began. No automatic merge is authorized.

## Phase 1 foundation retained

Registration/login/logout, BCrypt, HttpOnly/SameSite sessions, CSRF and owner-scoped resources; USD portfolio creation and one atomic deposit; three fictional assets with 3,129 deterministic 2022–2025 weekday closes; exact-date BUY/SELL with decimal settlement, chronological dates, idempotency and locking; FIFO cost/realized P/L; immutable executions, allocations, cash ledger and audit events; responsive execution desk, history/filtering/pagination, Flyway, OpenAPI, JSON logs, health, Docker/Compose and CI. All 25 original tests and four original browser cases remain in the full verification.

## Phase 2 delivered

- Persisted simulation baseline 2022-01-03, with initial capital at opening; actual creation/funding/execution/audit timestamps and balances preserved by additive V2.
- Historical cash, quantity, remaining FIFO cost and realized P/L reconstructed from immutable funding/executions/sell allocations through each effective date. Current mutable lot remainders are never used to infer the past.
- Exact-date marked positions; cash, market value and equity separately; realized/unrealized/total P/L; simple initial-capital returns and selected endpoint-period change/return.
- Authenticated owner-scoped valuation, daily value-series, combined performance, marked holdings and cash/asset allocation APIs. Repeatable-read snapshots; bounded date/asset/order queries; explicit missing funding/integrity failures.
- Explicit before-start/horizon/range validation; price gaps and weekends stay unknown while assets are held; closed/cash-only positions need no quote; zero denominators have named undefined states.
- Institutional-style dated dashboard: financial summary, capital/P&L breakdown, date presets/custom ranges, interactive equity chart with deposited-capital baseline and real hover/keyboard tooltips, marked-position table, allocation chart/value list and paginated accessible dated records. No hardcoded financial/chart values.
- Phase 1 execution desk remains available and clearly labeled as the latest executed state, separate from historical analytics. Explicit loading/empty/insufficient/error/retry states; desktop/mobile layouts.
- Updated architecture/API/data/financial/testing documentation, acceptance criteria, observed synthetic demo journey, screenshots and five Phase 2 interview questions with correct sample answers.

## Phase 2 verification (retained historical evidence)

| Check | Outcome |
| --- | --- |
| Full Maven verify | **55 passed**, 0 failures/errors/skips: 20 unit + 35 PostgreSQL integration; jar packaged |
| Required manual fixture | Cash 960, market 72, equity 1032, remaining cost 60, realized 20, unrealized 12, total gain 32, return 3.2000% |
| Phase 1 regressions | Original 25 backend tests and four browser cases retained and passed, including concurrent financial writes and fault-injected rollback |
| Historical correctness | No trades; buys; partial/full sells; multi-asset; same-day aggregation; future exclusion; missing prices/weekends; negative performance; zero values; invalid/range/order limits; authentication/ownership |
| Migration preservation | Real V1 fixture upgraded to V2; timestamps, funding, executions, balances and historical value retained |
| Existing local data | Independent restore/hash comparison retained every original row: 16 users/portfolios, 3 assets, 3,129 prices, 16 executions, 8 lots/allocations and 32 ledger/audit rows; original volume untouched |
| UI | Strict TypeScript and Vite production build passed; separate lazy chart chunk |
| Formatting/dependencies | Frontend Prettier passed; Java formatted; npm audit reported 0 vulnerabilities at validation time |
| Full browser suite | **6/6 passed** on desktop/mobile Chromium against production UI + Nginx + packaged Spring Boot + PostgreSQL |
| Browser financial journey | Compared UI to live API; inspected gaps/past state; chart hover/keyboard; invalid range and outage/retry; no JS/CSP errors or page overflow |
| Runtime | Prebuilt artifact images built/started; V2 applied; health UP and expected frontend HTML |

The observed ALTA demo (1,000 initial capital; buy 10 Jan 2, sell 4 Jan 3; mark Jan 8, 2024) produces 997.52 equity and −2.48 P/L from the actual synthetic dataset. This intentionally differs from the isolated 1,032 fixture; neither represents real market results.

## Rebuild and limitations

Codex install/start instructions and helper scripts are preserved in docs/CLOUD_ENVIRONMENT.md and the environment draft. Draft saves are not publication or fresh-task restoration proof. Local credentials/backups/database volumes remain outside Git; the repository contains reconstruction instructions and deterministic data generation, not private database contents.

This cloud's standard source-image Maven build was blocked by build-container DNS; the tested prebuilt-artifact path is documented. Managed Playwright download domains were previously denied; installed Chromium 154 ran the suites. GitHub Actions push and PR verification passed for implementation commit 3c810e873eb044edc6c80a874fe6542358b2d353, including managed Chromium browser tests; see TESTING.md for run links and PR #2 for current checks. Phase 2 native browser testing, public deployment and new-task restoration remain unverified.

At the Phase 2 milestone, local-development limits included one-JVM sessions, no account abuse/recovery controls, no dedicated runtime DB role or backup drills, and no pending-order reload recovery. Phase 4 addresses several of these below; independent penetration/accessibility/load audits remain absent. Exact-date price gaps are a deliberate convention, not silently repaired data.

## Not started

- Public target deployment, certificate operation, off-host backup verification and operator alerting remain pending explicit authorization and configuration.

Before Phase 4, no benchmarks, investment-return annualization, TWR/MWR, external market prices, additional cash flows or real trading were added. Phase 3 annualizes volatility only. Correlation matrices and variance attribution remain absent; exposure concentration is implemented.


## Phase 3 delivered

- Pre-code risk methodology with explicit decimal/float precision, nearest-rank percentile selection, sample thresholds, strict gaps and hand-verifiable fixtures.
- Pure Java engine for aligned consecutive-weekday asset/portfolio returns at fixed selected exposures; sample daily/annualized volatility and per-asset volatility; exposure weights/concentration including cash in equity.
- One-day historical VaR95/VaR99 in USD/equity percent, with 100/500 minimum observations, tail limitations and explicit cash-only/zero/missing states.
- Actual historical equity drawdown with peak/trough dates; missing weekdays invalidate the whole selected window. Future trades and prices are excluded throughout.
- Non-mutating stress simulations, three explicitly illustrative presets, common/per-asset sliders, saved custom comparisons and explained asset contributions. Decimal repricing preserves cash and zero-shock identity.
- Owner-scoped summary/returns/drawdown/stress endpoints; existing authentication, CSRF, transactions and limits reused. No schema changes or new services/dependencies.
- Cohesive Risk Lab tab, responsive real-data charts and tables, tooltips, keyboard controls, loading/unavailable/error/retry states. Original valuation/trading interfaces retained.
- Updated README/API/architecture/assumptions/teaching docs and five Phase 3 interview questions; reproducible running-app journey.

Phase 3 verification: **72 backend tests** (31 pure numerical + 41 PostgreSQL integration) passed with no failures/errors/skips; strict TypeScript/production build and formatting passed; **8 desktop/mobile Playwright cases** passed against packaged Spring Boot, PostgreSQL and production React/Nginx. All original 55 backend and six browser tests are included. Dependency scan fixes compatible inherited libraries; final Java scan retains two Spring MVC advisories on one package, explicitly reviewed/documented in [PHASE3_SECURITY.md](docs/PHASE3_SECURITY.md). npm audit and Gitleaks report no findings within their documented scope. See TESTING.md for actual commands and limits. No production-readiness claim; Phase 4 supersedes that dependency assessment.

## Phase 4 delivered

- Audited merged Phase 3 and successful main CI; committed the evidence-based plan before implementation (`d5f417a`).
- Supported Boot 4.0.8 / Framework 7.0.9 migration resolves both Spring MVC advisories. Expanded Java scan covers 158 including test/provided coordinates, zero findings/errors. Compatible Jackson/Tomcat maintenance patches retained all numerical/API contracts.
- Persistent atomic login/client/account throttles, temporary expiry, bounded request parsing, explicit cookie/header controls, isolated operations identity and negative ownership/security tests.
- Durable tab-local pending intent, exact owner-scoped idempotency lookup and real response-loss/refresh/backend-restart recovery. Original immutable history, locks, transactions and FIFO preserved. Read-only reconciliation detects cash/lot/reference drift without repairs.
- Safe request correlation, structured bounded logs, transaction completion metrics, separate readiness/liveness/database probes and protected operations metrics.
- Optional tested least-privilege runtime profile, migration-only process, non-destructive V3, private backup/checksum/new-database restore scripts and real disposable recovery/grant/migration tests. Compatible default local database owner mode remains an explicit limitation.
- Digest-pinned restricted runtime containers and actual image scans; unresolved base-image findings remain (including PostgreSQL helper-library Critical/High findings). APK remediation was blocked by official repository HTTP 403. See [security review](docs/PHASE4_SECURITY_REVIEW.md), not a production-readiness claim.
- Reproducible measured benchmark, operational/security/architecture/API documentation and five additional interview questions. No new infrastructure or financial-engine redesign.

Verification: **88 backend tests** plus a separate benchmark test passed; frontend TypeScript/build/formatting and npm audit passed; Gitleaks source/history clean. All **10 desktop/mobile browser tests** passed, including real backend restart recovery. Details are recorded in TESTING.md. Phase 5 supersedes that local delivery milestone below.

## Phase 5 local deployment preparation

The preimplementation plan is `083b6c0`, based on verified merged PR #4 and successful main CI. The distinct deployment keeps API/database/metrics private, enforces role/settings checks, separates migration secrets, validates a trusted HTTPS chain and canonical edge client identity, closes enrollment by default and preserves single-JVM reauthentication/order recovery. It explicitly accounts for non-root PostgreSQL initialization and writable data.

Final-image release gates require immutable source/IDs/platform/OCI checksums, full sanitized JSON/SBOM evidence, vulnerability/Java DB age ≤48h and zero Critical/High. Medium findings remain visible. Local encrypted recovery, compatible immutable-artifact rollback, real client isolation, DB outage probes, bounded load, accessibility and synthetic screenshots are independently evidenced; [release table](docs/PHASE5_RELEASE.md) records final results.

No host/domain or off-host backup destination/operator configuration is selected. Those target checks remain pending, not inferred from local rehearsal. The release recommendation remains **BLOCKED for public launch**. No merge, image publication, external backup upload, paid resource, visibility change or live deployment is authorized/performed.
