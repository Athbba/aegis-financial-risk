# Progress

Status: **Phase 0 and Phase 1 implemented and locally verified. Stop before Phase 2.**

## Completed

- Architecture, phased plan and acceptance criteria, API/data/financial/decision documentation.
- Java 21 / Spring Boot 3.5.16 modular backend, PostgreSQL 17, Flyway schema and validation.
- Registration, BCrypt hashing, session login/logout, CSRF, HttpOnly/SameSite cookies and portfolio ownership.
- Portfolio creation with one atomic initial USD deposit and audit/ledger records.
- Fixed-seed ALTA/BRIM/COVE synthetic weekday prices for 2022–2025 (3,129 observations).
- Historical-close BUY/SELL execution, decimal settlement, missing-price/insufficient-cash/oversell/backdate rejection.
- Portfolio-scoped idempotency replay/conflict, database locking and concurrent execution tests.
- FIFO lots, partial cost allocation, realized sell P/L, remaining holdings/cost basis.
- Immutable execution, sell-allocation, cash-ledger and audit records, with paginated owner-scoped APIs.
- Responsive dark React/TypeScript/Tailwind dashboard: creation, trading, holdings, record filtering/pagination, loading/empty/failure/success states and visible synthetic-data disclaimers.
- OpenAPI JSON, health check, structured logs, nonroot container definitions, Compose persistence, GitHub Actions workflow, screenshot evidence and phase mentoring/interview notes.

## Verification evidence

| Check | Actual outcome |
| --- | --- |
| Maven verify | 25 tests passed: 6 unit + 19 PostgreSQL integration; 0 failed/errors/skipped; executable jar built |
| Financial concurrency | Concurrent cash spending, selling and same-key requests passed on real PostgreSQL |
| Failure atomicity | Injected final audit insert failure rolled back cash/lots/execution/ledger/audit; same-key retry then succeeded |
| UI build | Strict TypeScript check + Vite production build passed |
| Formatting | Frontend Prettier check passed; Java source formatted with checksum-verified google-java-format 1.28.0 |
| Dependency audit | npm audit reported 0 vulnerabilities at validation time |
| Browser journey, native stack | 4/4 passed on desktop/mobile system Chromium |
| Runtime image build/startup | Prebuilt verified artifacts packaged successfully; nonroot frontend/backend started with Compose PostgreSQL |
| Browser journey, container stack | 4/4 passed through Nginx proxy against packaged application and production UI |
| Readiness/repeatability | Health UP + expected frontend HTML; seed rerun tested; repeated Compose startup retained data |

Initial test/build failures were diagnosed and fixed: missing JDK compiler/proxy trust configuration, validation-error handler ordering, and nonroot Nginx file ownership. An initial vulnerable Vite pin was replaced with 7.3.7 and audited again. No failing tests are ignored.

## Verification limits and environment notes

- Regular multi-stage source-image builds were blocked by Maven DNS resolution inside the cloud build container. `compose.prebuilt.yml` packages locally built/tested artifacts and was verified end to end. The default source-build completion is not claimed here.
- Managed Playwright browser downloads returned 403. Tests actually ran using installed Chromium 154; CI's managed-browser version has not been exercised in this session.
- GitHub Actions is configured, not remotely executed. No public deployment, publish action or new-task restoration has been verified.
- This is a local development security baseline: no public-production readiness claim. Session state is in one JVM. Rate limiting, account recovery/verification, dedicated runtime DB roles, backup/restore validation, broad security/load/accessibility testing and durable pending-request recovery remain later work.

## Reusable cloud configuration

The environment draft now contains tested `install_script` and `start_skill` instructions for tooling, frozen dependency installation, validation, artifact packaging and service startup. This draft save does not apply or publish a snapshot. Review/save it in environment settings and publish the environment to retain the configuration. Publication and restoration in a fresh task have not been verified.

## Not started

- **Phase 2:** dated portfolio valuation, unrealized P/L, returns, historical performance and interactive charts.
- **Phase 3:** allocation analytics, volatility, maximum drawdown, historical VaR, stress testing and scenario comparison.
- **Phase 4:** expanded security, audit/observability and reliability controls.
- **Phase 5:** final visual/accessibility refinement, optional light theme, deployment and presentation polish.

No external market data or paid credentials are required. All default data remains synthetic; no real trades are possible. See docs/MENTORING.md for what to learn and five interview questions for each completed phase.
