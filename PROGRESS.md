# Progress

Status: **Phases 0, 1 and 2 implemented and locally verified. Stop before Phase 3.**

Phase 1 was merged into main by PR #1 (merge ce45a8e2d3e605b4f48f063deb4e9115fc160085). Phase 2 starts from that verified merge on feature/aegis-phase-2; it does not overwrite the Phase 1 feature branch or automatically merge.

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

## Actual local verification

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

This cloud's standard source-image Maven build was blocked by build-container DNS; the tested prebuilt-artifact path is documented. Managed Playwright download domains were previously denied; installed Chromium 154 ran the suites. Phase 2 native browser testing, managed-browser CI, public deployment and new-task restoration are not claimed by local results. Current remote CI status is reported with the delivery PR rather than assumed from workflow presence.

Local-development security limits remain: one-JVM sessions, no account abuse/recovery controls or independent penetration/accessibility/load audit, no dedicated runtime DB role/production backup drills. Pending order recovery across browser reloads remains future reliability work. Exact-date price gaps are a deliberate convention, not silently repaired data.

## Not started

- **Phase 3:** volatility, drawdown, historical VaR, correlation, risk attribution and stress/scenario simulations.
- **Phase 4:** expanded security, observability, audit operations and reliability.
- **Phase 5:** final visual/accessibility refinement, optional light theme, deployment and presentation.

No benchmarks, annualization, TWR/MWR, external market prices, additional cash flows or real trading were added. Phase 3 requires a separate request.
