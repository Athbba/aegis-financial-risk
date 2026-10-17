# Testing and verification

## Commands

From the repository root, with a full JDK 21, Maven 3.9+, Node 24 and Docker:

```bash
mvn -B -ntp -f backend/pom.xml verify
# Prepared cloud alternative (same pom/suites, correct JDK/proxy/trust/cache):
./scripts/cloud-maven.sh -B -ntp verify
npm --prefix frontend ci
npm --prefix frontend run build
npm --prefix frontend run format:check
npm --prefix frontend audit

# Start PostgreSQL, packaged API and production UI as documented in README first.
cd frontend
npx playwright install --with-deps chromium
npm run test:e2e
# Cloud uses the installed browser when managed downloads are denied:
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium npm run test:e2e
```

Maven verify executes all JUnit suites through Surefire and packages the jar. All integration class names end in Test, so the default inclusion executes them; no Docker-disabled skip is enabled. Testcontainers supplies isolated PostgreSQL 17 instances, Flyway and the real persistence/security stack. Tests never use the Compose application's database or H2 substitutes. Phase 1 tests are retained unchanged.

## Layers and actual scope (Phases 1–3)

| Suite | Count | Coverage |
| --- | --- | --- |
| FinancialMathTest | 5 | HALF_EVEN settlement, invalid/sub-cent orders, multi-lot FIFO, remainder preservation, realized loss/overselling |
| SyntheticPricesTest | 1 | Fixed-seed reproduction, 3,129 observations, symbols, positive four-decimal weekday closes/date bounds |
| PlatformIntegrationTest | 19 | Atomic funding/FIFO/reconciliation, rejections, idempotency, chronology/gaps, concurrent buys/sells/duplicates, ownership, CSRF/auth, validation, immutable history, repeatable seed, pagination, fault-injected rollback, OpenAPI strings |
| ValuationEngineTest | 14 | Hand-verifiable $1,032/3.20% scenario, flat capital, future-trade exclusion, all/partial missing marks, closed positions, order-independent same-day aggregation, weekend gaps, losses, position rounding, endpoint vs capital return, undefined/zero denominators, reconciliation failures |
| AnalyticsIntegrationTest | 16 | Real historical execution/allocation reconstruction; same required scenario; no trades; buy-only/multiple assets; partial/full sells; same day; weekday/weekend gaps; losses; range conventions/bounds; every new route's authentication/ownership; invalid parameters; hard execution cap; missing endpoints; V1-to-V2 preservation; zero-value allocation/range semantics |
| RiskEngineTest | 11 | Pre-code SD/annualization, weekday alignment, cash weights, strict gaps even with enough samples, exact VaR ranks/minimums, peak/trough recovery, zero equity/rounding, null/invalid stress inputs and cash-only analytical zeros |
| RiskIntegrationTest | 6 | PostgreSQL full-window metrics, dedicated endpoints, future trade/price exclusion, weekday gaps vs weekends, manual stress fixture/full-row immutability, every route ownership/auth/CSRF, date/window/scenario bounds |
| Playwright | 8 | Four scenarios × desktop Chromium / Pixel 7 emulation: original trading/auth/history, missing execution prices, historical performance and Risk Lab/scenario comparison |

Concurrency uses real PostgreSQL row locks and two threads. Fault injection rejects the final audit insert and checks every preceding financial write rolls back, then permits a same-key retry. These remain part of the full suite, not mocked approximations.

## Phase 2 browser journey

The new test registers and creates a USD 1,000 portfolio, verifies flat initial capital/zero gain, buys 10 ALTA on 2024-01-02, sells 4 on 2024-01-03, selects Jan 1–8 and compares every financial panel with the **actual API response**. These are the real fixed-seed synthetic assets, not injected fixture chart data.

It then inspects dated records and weekend gaps, selects a date before the trades to prove future exclusion, selects a missing-price weekend to check unknown totals/allocation, rejects reversed ranges, exercises chart mouse/keyboard tooltips, injects an explicit HTTP outage to test retry, and captures the real restored dashboard. Error injection supplies no fabricated financial numbers. Tests also check JavaScript/CSP errors and page-level horizontal overflow. Original journeys check HttpOnly/SameSite cookies, no localStorage credentials, oversell rejection, ledger/audit records and re-login persistence.

The chart's readable dated-value table and allocation list support nonvisual use; this is not a full external accessibility or penetration audit. Mobile is emulation, not physical device validation; Firefox/WebKit remain unverified. GitHub CI additionally ran the workflow using its managed Chromium.

## Executed locally — 17 October 2026

- **Backend: 55 tests passed**, 0 failures/errors/skipped (20 unit + 35 PostgreSQL integration). The executable jar was built.
- **Frontend: production build and strict TypeScript passed.** Recharts is split into a lazily loaded chart chunk; it does not load on the login path.
- **Frontend formatting passed.** Java sources were formatted with checksum-verified google-java-format 1.28.0.
- **npm audit: 0 reported vulnerabilities** for the locked frontend tree at verification time. This is not a backend/container security audit.
- **Browser: 6/6 passed** against nonroot Nginx, the production React bundle, packaged Spring Boot and PostgreSQL. System Chromium 154 was used. The four original Phase 1 cases are included, not replaced.
- **Migration: passed** on an isolated V1 fixture, retaining real timestamps, cash, original orders, ledger and historical cost/value.
- **Existing local data: preserved.** A pre-upgrade custom PostgreSQL backup was restored into a separate temporary database, all original row hashes were compared against the upgraded app (except the added portfolio baseline column), and the temporary copy was removed. The original 16 accounts, 3 assets, 3,129 prices, 16 portfolios, 16 executions, 8 lots, 8 allocations, 32 ledger entries and 32 audit events matched. No original database volume was removed.
- **Container packaging/startup passed.** The tested prebuilt-artifact Compose path applies V2 and retains existing data. Health UP and expected frontend HTML were verified; browser tests provide functional evidence beyond health.

The Phase 2 container/browser suite is retained historical evidence; Phase 3 results below are current. Phase 1 was also tested on the native Vite stack earlier; Phase 2 native browser testing is not claimed. GitHub Actions push and pull-request runs passed for implementation commit `3c810e873eb044edc6c80a874fe6542358b2d353`: [push verification](https://github.com/Athbba/aegis-financial-risk/actions/runs/38989766913) and [PR verification](https://github.com/Athbba/aegis-financial-risk/actions/runs/38989784943). Both completed the backend, frontend build/formatting, managed-browser install, container startup and desktop/mobile browser stages. Current checks are available on [PR #2](https://github.com/Athbba/aegis-financial-risk/pull/2); workflow presence alone is not a passing CI claim. No public deployment or new-cloud-task restoration is claimed.

## Environment constraints and results

The original machine supplied a JRE but lacked a compiler. The full official Temurin 21 JDK was prepared from a verified Docker image. Maven 3.9.11 was SHA-512 checked and uses the existing egress proxy/system trust store, with TLS verification enabled. Source-image Maven DNS resolution failed inside this cloud's build container; compose.prebuilt.yml packages the locally verified jar/UI and was exercised. The source-image path remains available for ordinary Docker networking, but success here is not claimed. Managed Playwright downloads previously returned 403; installed Chromium is the documented, actually executed fallback.

Reports: backend/target/surefire-reports/ and frontend/playwright-report/. Browser traces/screenshots: frontend/test-results/. Repository screenshots: docs/images/phase2-desktop.png and phase2-mobile.png. Local database backups stay outside the Git checkout and are not published.

Preserve command exit status (`set -o pipefail` when using tee), verify nonzero test counts and inspect current-run reports. Do not disable assertions, database constraints, checksums or TLS verification. Do not edit an applied migration to repair state; add another migration after this baseline. Do not infer functional correctness from a PID or open port.


## Phase 3 execution evidence — 2026-10-17

- `./scripts/cloud-maven.sh -B -ntp verify`: **72 passed, 0 failures/errors/skips**, including all 55 Phase 1–2 regressions. Current split: 31 pure numerical tests and 41 real PostgreSQL tests. Repeated after compatible dependency updates; jar repackaged successfully. No H2, mocked market results or Docker-disabled test skipping.
- `npm --prefix frontend run build`: strict TypeScript and Vite production build passed. `npm --prefix frontend run format:check`: passed. New Java sources/tests formatted with the existing checksum-verified google-java-format tool. `git diff --check`: passed.
- Prebuilt Compose packaging and health check: passed with the current jar and UI. Existing local database volume/credentials retained; Phase 3 requires no migration.
- `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium npm --prefix frontend run test:e2e`: **8/8 passed**, desktop Chromium and Pixel 7 emulation, against actual packaged API, PostgreSQL and Nginx production UI. All six previous cases remain. Phase 3 captures `phase3-risk.png` in each project's test-results directory; selected screenshots are in docs/images.
- Risk browser journey: cash-only zero risk; two real synthetic buys; displayed volatility/VaR/drawdown compared to live API; 521 common shocks; nearest-tail ordering; keyboard tooltip and slider; custom BRIM override and named scenario comparison; zero shock identity; unchanged order history; insufficient samples; missing weekend marks; future-trade exclusion; injected transport outage and real-data retry; no page JS errors or horizontal page overflow.
- Numerical fixture: cash500 / A300 / B200 and shocks A−10%, B+20% → stressed equity1010, signed gain10 / +1%. Drawdown [100,120,90,110,80,130] → 33.3333%, second-day peak/fifth-day trough. SD [.01,.02,.03] → .01, annual fraction .15874507866387544. Missing weekday gaps cannot become multi-day “daily” shocks, including when >30 other observations survive.
- Database immutability check serializes complete portfolio, execution, lot, allocation, cash-ledger and audit rows before/after accepted/rejected stress calls. Tests check 401/404 on all new endpoints, 403 without CSRF for stress, and bad/unsupported date/window/scenario input.
- `npm audit`: zero reported frontend vulnerabilities. Java: 87 runtime coordinates checked against reviewed GitHub advisories, no query errors, compatible fixes applied; **two remaining Spring MVC advisories** recorded in [PHASE3_SECURITY.md](docs/PHASE3_SECURITY.md). Gitleaks 8.30.1 source/history checks: no leaks. Runtime Java inventory/results and reproducible scan commands are linked there. Repository Dependabot alerts are disabled (403); container OS/test-only dependency audits are not claimed.

The prepared cloud's existing JDK21/Maven, system Chromium and prebuilt-image path were reused successfully. No fresh managed-browser download or standard source-container dependency build was retried for Phase 3; their earlier environment limits remain. Browser emulation is not a physical-device audit. No load/penetration audit, public deployment or production-readiness claim. GitHub Actions results must be checked on the Phase 3 PR; local passing results do not establish remote CI success. Stop after Phase 3.
