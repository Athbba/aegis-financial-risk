# Testing and verification

## Commands

Run from the repository root unless a different directory is shown.

```bash
# Full JDK 21 and Maven 3.9+, Docker available:
mvn -B -ntp -f backend/pom.xml verify
# Prepared cloud alternative:
./scripts/cloud-maven.sh -B -ntp verify

npm --prefix frontend ci
npm --prefix frontend run build
npm --prefix frontend run format:check
npm --prefix frontend audit

# Start the app with the README's native or container instructions first:
cd frontend
npx playwright install --with-deps chromium
npm run test:e2e
# Cloud: system Chromium is available when Playwright downloads are denied:
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium npm run test:e2e
```

Maven `verify` runs all JUnit tests via Surefire and packages the executable jar. The integration class is intentionally named `PlatformIntegrationTest` so it runs with the default `*Test` inclusion. No Docker-disabled skip flag is set: absent Docker causes a visible failure. Testcontainers provisions PostgreSQL 17, applies Flyway, loads deterministic data and removes the isolated database afterward. It does not touch the Compose development database. Passwords/accounts created by tests are test fixtures only.

## Test layers

| Suite | Cases and evidence |
| --- | --- |
| FinancialMathTest (5) | HALF_EVEN settlement, invalid/sub-cent orders, manually verifiable multi-lot FIFO, rounding remainder conservation, realized loss/overselling |
| SyntheticPricesTest (1) | Fixed-seed reproducibility, exact 3,129 count, 3 assets, positive four-decimal weekday prices and date bounds |
| PlatformIntegrationTest (19) | Atomic funding; exact-date FIFO trades and reconciled cash; rejected transactions; idempotent replay/conflict; chronological dates; missing-price gaps; concurrent buys, sells and same-key requests; ownership across all routes; CSRF/anonymous access; invalid input; login/logout/hash; immutable history; repeatable seed; pagination; rollback after injected final audit failure; missing keys/unknown fields/unsupported methods; OpenAPI decimal schema |
| Playwright (4) | The same two scenarios on desktop Chromium and Pixel 7 mobile emulation: full account/fund/buy/reject-oversell/sell/history/filter/logout/login journey, plus a missing-price gap disabling execution |

Concurrency tests use two real threads, a start latch, separate Spring transactions and a real PostgreSQL row lock. They verify committed balances/positions/history rather than simply verifying a mock lock call. The audit fault test fails at the final database insert and proves preceding JPA/JDBC accounting writes roll back, then retries the same request key successfully.

The browser journey also checks HttpOnly/SameSite authentication cookies, absence of localStorage auth data, no JavaScript page errors and no page-level horizontal overflow. It takes screenshots from actual application state. These are functional browser checks, not comprehensive penetration, accessibility, load or cross-browser tests.

## Executed locally — 17 October 2026

- **Maven verify: passed.** 25 tests, 0 failures, 0 errors, 0 skipped. 6 unit tests + 19 PostgreSQL integration tests; executable jar packaged. Full JDK 21 and Spring Boot 3.5.16.
- **Frontend build/type check: passed.** TypeScript strict mode and Vite 7.3.7 production bundle.
- **Formatting: passed.** Prettier check for all frontend source/config/browser-test files.
- **npm audit: passed.** 0 vulnerabilities reported for the locked dependency tree at verification time; this is not a backend/container security audit.
- **Native-stack browser checks: passed.** 4/4 against the real Java application, Compose PostgreSQL, and Vite. System Chromium 154 used because the managed Playwright download domains returned 403. No tests were replaced or skipped.
- **Runtime image build: passed.** The prebuilt artifact targets package the verified jar and frontend build; nonroot containers start with the Compose database.

- **Container-stack browser checks: passed.** The same 4/4 tests also passed against nonroot Nginx + packaged Spring Boot + PostgreSQL via Compose, exercising the production UI bundle and reverse proxy.
- **Startup readiness: passed.** Backend/database health reported UP, frontend returned the expected AEGIS HTML and CSP/security headers. Repeated Compose startup preserved the existing database and synthetic rows.

Container browser results and final startup checks are also recorded in PROGRESS.md. GitHub Actions configuration is present but has not been executed by this local session. No public deployment or fresh-cloud-task restoration is claimed.

## Diagnosed environment limitations

The initial machine provided a Java runtime but no usable JDK compiler. A full official Temurin 21 JDK was prepared from its verified Docker image. Maven 3.9.11 was downloaded from Maven Central and SHA-512 checked. Maven uses the configured egress proxy and the existing system Java trust store; TLS verification stays enabled.

The regular multi-stage Docker source build could not resolve Maven Central from its build container. `compose.prebuilt.yml` is the tested packaging alternative after the native builds/tests. The standard source-image path is provided for ordinary Docker networks, but its completion was not verified here. Managed Playwright browser download access was denied; installed system Chromium exercised the actual browser journeys instead. CI installs the Playwright-managed browser on its own runner.

## Results and troubleshooting

- JUnit reports: `backend/target/surefire-reports/` (current run XML and text).
- Playwright report: `frontend/playwright-report/`; failure traces and screenshots: `frontend/test-results/`.
- Repository screenshots: `docs/images/` (synthetic demonstration only).
- Backend health: `curl --fail http://localhost:8080/actuator/health`.
- Container startup: `docker compose ps -a` and `docker compose logs --tail=100`.

Exit status must be preserved when capturing output. Do not infer success from an open port, a partial log or a test file merely existing. If capturing through `tee`, enable `set -o pipefail`. Do not edit an applied Flyway migration to repair local state; add a new migration for schema changes after this baseline is committed. Do not disable constraints, verification or assertions to make tests pass.
