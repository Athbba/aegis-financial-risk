# Phase 5 deployment blockers and release evidence

Baseline verified before implementation: main `b3151f55bf63709808b6279eb3970972db3162aa` contains reviewed Phase 4 `558217b`; no later changes. [Merge CI](https://github.com/Athbba/aegis-financial-risk/actions/runs/39054875143) passed. Branch: `feature/aegis-phase-5`. This plan is committed before code.

## Design and blockers

One host, one API, PostgreSQL, React/Nginx; separate short-lived migration/grant process and non-owner serving identity. A distinct deployment Compose file exposes only a same-origin HTTPS edge (loopback for rehearsal), redirects HTTP, keeps API/database/metrics private and loads secrets from private files. Sessions are JVM-local: restart/rollback requires reauthentication, while pending order keys survive in tab storage. No shared sessions, paid infrastructure, image publication or public launch.

Release is BLOCKED until evidence establishes: final immutable image scans have zero Critical/High and DB age ≤48 hours; effective database privileges and trusted proxy client budgets pass; trusted TLS/cookies/ownership and restart recovery pass; encrypted off-host backups have configured storage, observed freshness and a verified restore; upgrade/rollback, readiness/liveness and bounded load satisfy declared criteria. Missing target host/domain/certificate/storage/operator inputs remain UNVERIFIED, never inferred from local rehearsal.

## Work and measurable acceptance

| Work | Acceptance and evidence |
| --- | --- |
| Images | Investigate exact pebble/gosu/zlib findings, prefer supported patched publishers; verify removed tools are unused. Scan final API/UI/DB images, keep sanitized full JSON + SBOM artifacts. Fail closed for scanner errors, missing/stale DB, Critical/High. No ignore-unfixed/ignore list/TLS bypass. Commit compact identity/finding summary. |
| Privileges | Reject privileged membership/SET ROLE, ownership and excess effective grants; refuse unsafe deployment settings. Real narrow-role auth/BUY/partial SELL/reconcile, negative DDL/trigger/price/history mutation, and browser recovery. Explicit PostgreSQL initialization/write/user model. |
| HTTPS/abuse | Trusted local CA and SAN certificate; no insecure browser/curl option. Replace client forwarding at edge, trust only isolated proxy peer(s). Account budgets retained; distinct clients isolated, spoofing denied, restart/expiry tests. Enrollment defaults closed, explicit limited demo enrollment only. |
| Recovery/operations | Scheduled authenticated encrypted off-host backup configuration, retention/failure/age checks; disposable encrypted roundtrip and financial fingerprints/source unchanged. Demo RPO 24h, RTO 30m; actual storage and target-host evidence required. Immutable-artifact upgrade/rollback without down migration, same-key response recovery; DB outage readiness fails/liveness stays healthy. |
| Smoke/presentation | Bounded local concurrency 4 clients ×20 read requests; <1% errors and p95 <1000ms (rehearsal only). Health/backup/disk/error alert rules. Keyboard/contrast/mobile review, real synthetic screenshots, reproducible demo and interview notes. |
| CI/delivery | Retain all 88 backend/10 browser cases, add deployment controls. Type/build/format, exact Java incl. tests, npm, redacted secrets, final-image and deployment smoke gates; negative vulnerable/error/stale/unsafe fixtures must fail. Safe artifacts only. Draft PR to main with PASS/FAIL/UNVERIFIED table and exact checks; no merge/launch. |

## Verification commands

`./scripts/cloud-maven.sh -B -ntp verify` (portable: `mvn -f backend/pom.xml verify`); `npm --prefix frontend run build`; `npm --prefix frontend run format:check`; `npm --prefix frontend audit`; exact `dependency:list` inventory + `scripts/check-java-advisories.py`; Gitleaks source/history redacted; final Trivy JSON + CycloneDX SBOM and strict evidence validator; deployment script/gate tests; isolated HTTPS Compose rehearsal plus all Playwright cases with backend restart. Commands added during implementation are documented in TESTING.md and the release evidence table.

Never operate on the existing Compose project/volume during drills, restore over a database, weaken accounting tests or publish credentials/traces/dumps. Independent work continues despite missing operator inputs; release exceptions require explicit user acceptance. Stop before merge/public launch.
