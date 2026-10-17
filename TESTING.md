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

The Phase 2 container/browser suite is retained historical evidence; current Phase 4 results are below. Phase 1 was also tested on the native Vite stack earlier; Phase 2 native browser testing is not claimed. GitHub Actions push and pull-request runs passed for implementation commit `3c810e873eb044edc6c80a874fe6542358b2d353`: [push verification](https://github.com/Athbba/aegis-financial-risk/actions/runs/38989766913) and [PR verification](https://github.com/Athbba/aegis-financial-risk/actions/runs/38989784943). Both completed the backend, frontend build/formatting, managed-browser install, container startup and desktop/mobile browser stages. Historical checks are available on [PR #2](https://github.com/Athbba/aegis-financial-risk/pull/2); workflow presence alone is not a passing CI claim. No public deployment or new-cloud-task restoration is claimed.

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

The prepared cloud's existing JDK21/Maven, system Chromium and prebuilt-image path were reused successfully. No fresh managed-browser download or standard source-container dependency build was retried for Phase 3; their earlier environment limits remain. Browser emulation is not a physical-device audit. No load/penetration audit, public deployment or production-readiness claim. GitHub Actions results must be checked on the Phase 3 PR; local passing results do not establish remote CI success. Phase 3 stopped at its delivery; Phase 4 results follow.

## Phase 4 execution evidence — 2026-10-17 (current)

Earlier phase results above are retained historical evidence. Phase 4 started only after PR #3 was merged and main CI passed. See [audit plan](docs/PHASE4_PLAN.md), [security review](docs/PHASE4_SECURITY_REVIEW.md), [operations/recovery runbook](docs/PHASE4_OPERATIONS.md) and [measured benchmarks](docs/PHASE4_BENCHMARKS.md).

- Full `./scripts/cloud-maven.sh -B -ntp verify`: **88 passed, zero failures/errors/skips**, retaining all original 72. Split: 31 numerical/unit and 57 PostgreSQL integration. Repeated after the Boot 4/Jackson/Tomcat migration and final pool configuration. `EngineeringSecurityTest` adds 13 cases; `DatabaseSafetyTest` adds 3. The opt-in `EngineeringBenchmark` is separately executed (one additional passing benchmark test), excluded from normal Surefire discovery by its name.
- Actual PostgreSQL tests cover persisted/concurrent authentication throttling and expiry, restart-style limiter recreation, unknown-account budgets, spoofed forwarded addresses, CSRF/ownership and negative foreign writes, bounded streamed/known-length bodies, JSON constraints, secure-cookie settings, safe errors/logs/correlation IDs, protected operations metrics and committed/replayed/rejected/rolled-back outcomes with an injected audit failure.
- Database drills use isolated Testcontainers databases: V2→V3 preserves financial rows; real app authentication/buy/partial FIFO sell work under narrow runtime grants; DDL and prohibited history/price writes fail; actual custom-format backup/restore scripts preserve fingerprints and refuse active/existing restore targets. No destructive recovery action touched the active application volume.
- Full desktop/mobile Playwright suite: **10/10 passed (1.4 minutes)** against the running production Nginx/React, packaged API and PostgreSQL stack, with real backend restart enabled. All eight earlier cases remain. An earlier run had a missing-CSRF-cookie test setup error (corrected by fetching the token after login rotates it); a subsequent run had one cold-start timeout, resolved by the documented readiness check. The final full run passed without retries.
- Strict TypeScript, Vite production build and frontend Prettier check passed. Java changes were formatted with google-java-format. No financial engine or formula was rewritten. `npm audit` reports **zero vulnerabilities**, including development dependencies.
- Java advisory checks cover **158 compile/runtime/test/provided coordinates**, **zero findings/query errors**. The two Spring advisories are patched by supported Framework 7.0.9, with the complete Boot 4 migration tested. See [exact inventory](docs/phase4-java-audit.json); severity/exposure distinctions and compatible maintenance updates are documented in the security review.
- Trivy **0.76.0** was checksum-verified and scanned all three actual runtime images. Findings remain: API 3 High / 61 Medium / 5 Low / 1 Unknown; UI 1 Medium; PostgreSQL 1 Critical / 24 High / 31 Medium / 3 Low / 2 Unknown. [Exact sanitized inventory](docs/phase4-image-audit.json) and the security review identify affected Go helper binaries and limitations. Official Alpine package repositories returned 403 during a disposable supported zlib upgrade attempt. These are unresolved vulnerabilities, not a clean container scan.
- Gitleaks **8.30.1**, redacted source-export and available Git-history checks: **zero findings**. The export includes tracked and nonignored new source, excludes local secrets/build artifacts/browser traces/dumps, and remains outside Git. Repeat history scan after committing. No credentials or private database records are included in the delivery. Browser traces may contain throwaway credentials/cookies: local failed-test traces stay ignored; CI disables trace capture to prevent uploading them.
- The explicit disposable benchmark passed for creation, BUY, partial FIFO SELL, historical valuation/performance, risk, eight-scenario stress and paginated history. Measurements are serial local MockMvc+PostgreSQL timings, not invented throughput or a production load test. See the environment, raw samples and limits in the benchmark document.

Reproduce the current browser checks only after packaging and readiness:

```bash
./scripts/wait-ready.sh
AEGIS_E2E_RESTART_BACKEND=true PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium npm --prefix frontend run test:e2e
# Ordinary CI uses its managed Chromium; omit PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH there.
```

The recovery flag deliberately restarts the serving backend, so use a disposable/development stack, not a shared active service. Tests retain the eight Phase 1–3 cases and add desktop/mobile response-loss recovery. The new journey aborts a request before submission, reloads with an unresolved key, retries the exact saved intent, drops a response **after the real commit**, restarts the backend, signs in again and verifies precisely one execution, matching ledger/audit records and CONSISTENT reconciliation.

Environment limits remain: prebuilt artifact images were used after verified local builds because the earlier source-container Maven DNS path was unavailable; system Chromium replaces blocked managed downloads. No physical-device/Firefox/WebKit, independent penetration audit, public deployment, offsite/PITR recovery or concurrent capacity certification is claimed. Trivy's default mirror returned 403; supported official GHCR DB repositories succeeded with TLS verification enabled:

```bash
trivy image --db-repository ghcr.io/aquasecurity/trivy-db:2 \
  --java-db-repository ghcr.io/aquasecurity/trivy-java-db:1 \
  --scanners vuln --format json --output /tmp/aegis-image.json IMAGE
```

CI remains required on the delivered commit; local results alone do not establish remote CI success. Stop before Phase 5.

## Phase 5 verification (current delivery evidence)

Baseline main `b3151f5` and its [merge CI](https://github.com/Athbba/aegis-financial-risk/actions/runs/39054875143) were verified before implementation; the Phase 5 plan was committed first (`083b6c0`). The original 88 backend and 10 browser tests remain. Phase 4's all-container hardening claim was corrected: its PostgreSQL service did not share the API/UI restrictions.

Commands and artifact policy: [deployment runbook](docs/PHASE5_DEPLOYMENT.md), [security gate](docs/PHASE5_SECURITY.md), [release table](docs/PHASE5_RELEASE.md). All tests use isolated PostgreSQL/Compose projects; original credentials/volumes are preserved. New `DatabaseSafetyTest` cases activate the actual deployment startup guard, retain real restricted-role trading, and reject NOINHERIT/SET ROLE membership, database/schema ownership, table/column/PUBLIC-derived excess rights and unsafe settings. `ProxyBoundaryTest` rejects spoofed/malformed canonical addresses. Complete backend verify currently executes **91 tests**, no disabled Docker tests.

Python gate fixtures deliberately fail vulnerable output, stale/missing/mismatched evidence, scanner/network failure and unsafe configuration. The release job is separate from ordinary regression CI and depends on its success: build/type/format, exact Java including test/provided dependencies, npm audit, verified scanners, source/history secret scans, a real isolated HTTPS deployment, all browser cases plus accessibility, separate proxy clients, database outage probes, encrypted backup/restore, immutable-artifact rollback and final-image JSON/SBOM/OCI gates. Missing evidence/DB >48h/Critical/High/scanner errors block release. No ignore list or insecure transport option is used.

Local evidence is generated under ignored `.local/phase5/` and compact sanitized results are committed at delivery. The environment restarted once during this task: old `/tmp` evidence/secrets disappeared while source/images/original volumes survived. A **new** isolated project/subnet/database was created; the previous rehearsal and original financial volumes were retained. Previously passing results were not substituted for missing final artifacts. Earlier test setup failures (CA database filesystem permissions, proxy interception of a loopback health probe, private test CA readability and pg_restore stdin adapter) were corrected and rerun. The mobile Axe test found a real nonfocusable scroll region, which was corrected without weakening its rule.

The local client-isolation test makes 32 attempts from one real edge-network client: 30 unauthorized then two throttled despite forged headers; another client authenticates; the first remains blocked after API restart. Existing persistent-window controlled-clock PostgreSQL tests verify expiry. Effective API/UI/PostgreSQL UIDs/capabilities/filesystems/resources are inspected. A real database stop removes readiness while liveness stays UP. No active user's volume is stopped/deleted/restored; only the isolated test database is interrupted.

The encrypted drill compares nine full financial-table fingerprints, restores a new database, checks cash/FIFO/ledger/audit references, rejects existing targets and damaged ciphertext, and verifies the source unchanged. RPO 24h / RTO 30m are declared demo targets; local measurement does **not** establish remote backup age, offsite key custody/retention or target-host recovery. The concurrent smoke has four workers ×20 reads, error ratio <1%, p95<1000ms; it is bounded rehearsal, not a capacity certification.

Final counts, scan identities/DB timestamps, timings and exact CI/PR URLs are in [PHASE5_RELEASE.md](docs/PHASE5_RELEASE.md). Target host/domain/certificate renewal, off-host storage/restore and operator alert routing remain UNVERIFIED by explicit user direction. No images/backups were externally published, services purchased, repository visibility changed or public environment deployed.

Phase 5 final local results: **91 backend / 12 HTTPS browser tests / 4 negative gate tests passed**; 158 Java coordinates and npm audit clean; source-identified serving/rollback image gates report zero Critical/High and one Medium zlib each. Actual measurements: 80 reads/four workers, zero errors, p95 219.765ms; encrypted restore 2.835s, nine financial fingerprints/reconciliation equal, source unchanged. Source IDs/timestamps/checksum scope are in [the image summary](docs/phase5-image-summary.json) and [local report](docs/phase5-local-rehearsal.json). Final delivered-commit GitHub URLs/status are in the draft PR; target release remains blocked.
