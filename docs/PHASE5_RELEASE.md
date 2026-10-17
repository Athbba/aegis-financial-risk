# Phase 5 release recommendation and blocker table

**Recommendation: BLOCKED for public release.** A target host/domain, real certificate operation, encrypted off-host destination and operator configuration are not selected. The user explicitly requested local preparation only. No deployment, upload, purchase, image publication or external infrastructure change is authorized or performed. A draft PR is the delivery artifact; do not merge or launch automatically.

Baseline: `b3151f55bf63709808b6279eb3970972db3162aa`, no later main changes at verification; [successful main CI](https://github.com/Athbba/aegis-financial-risk/actions/runs/39054875143). Pre-code plan: `083b6c0`. All statuses below distinguish local rehearsal from target deployment.

| Blocker/control | Status | Evidence | Remaining action |
| --- | --- | --- | --- |
| Verified merged Phase 4 baseline | PASS | Main CI above; [plan](PHASE5_PLAN.md) | Recheck main before merge |
| Final immutable image evidence, zero Critical/High, DB ≤48h | PASS | Local source-3694b2e gate; [identity/finding summary](phase5-image-summary.json) | Refresh before release; CI artifact evidence follows |
| Runtime role, membership/effective privilege rejection | PASS | `DatabaseSafetyTest`, `DeploymentGuard`, [runbook](PHASE5_DEPLOYMENT.md) | Verify selected host/role configuration |
| Private API/DB, non-root initialization and container limits | PASS | `rehearse-deployment-controls.py` effective inspections | Validate target filesystem/volume ownership and firewall |
| Trusted HTTPS, secure cookies, proxy client isolation | PASS | Real trusted-CA stack/browser; separate client spoof/throttle/restart rehearsal | Domain/public certificate, canonical redirect, external proxy/firewall review |
| Closed enrollment and single-instance session/recovery policy | PASS | Default closed; explicit local test window; [runbook](PHASE5_DEPLOYMENT.md) | Select operator and supervised enrollment procedure |
| Encrypted fresh-database restore/source preservation | PASS | `rehearse-encrypted-recovery.py`, nine table fingerprints and reconciliation | Off-host roundtrip, prepared target recovery timing |
| Scheduled protected off-host backup, retention, RPO | UNVERIFIED | Timer/service, encrypted upload/hash verification, failure/age checks provided | Choose destination/keys/account, authorize transfer, verify retention and observed age ≤24h |
| Upgrade/rollback, same-key recovery, retained trade | PASS | Local `rehearse-upgrade.py` retains one execution and reconciliation through each transition | Target rehearsal remains unverified |
| Readiness vs liveness during DB outage | PASS | Real DB stop: readiness down, liveness UP | Configure target health/alert routing; don't restart solely for readiness |
| Concurrent smoke thresholds | PASS | `deployment-smoke.py`: four clients ×80 total reads, <1% errors, p95<1s | Commit final measured report; target workload remains unverified |
| All local regressions and negative release gates | PASS | Final local 91 backend / 12 browser, four negative fixtures; original 88/10 retained | GitHub checks separately assessed on delivered commit |
| Keyboard, contrast/mobile and synthetic screenshots | PASS | Both desktop/mobile Axe/keyboard/contrast checks; real masked screenshots | Independent screen-reader/physical-device audit remains unverified |
| Target host, certificate renewal, offsite restore, alert delivery | UNVERIFIED | User confirms none selected | Select/authorize/configure and test before public release |

Measured reports and immutable identities are below; exact delivered-commit GitHub check URLs are maintained in the draft PR. Historical Phase 4 reports remain historical; old clean Java results do not establish a clean current image or target environment. No production-readiness claim.

## Final local image gate

Source commit **`3694b2ec367b3ae6b1642124a12a2510f5ab8371`**. [Compact immutable image/platform/OCI/report/SBOM summary](phase5-image-summary.json). Trivy **0.76.0**; vulnerability DB updated **2026-10-17 18:34:28 UTC**, Java DB updated **2026-10-17 01:07:50 UTC**. The actual gate passed with both ages ≤48 hours. Each final API/UI/database image has **zero Critical/High, one Medium** (zlib CVE-2026-85091); no Low/Unknown findings. Full sanitized JSON, CycloneDX SBOMs and OCI checksums are retained as CI evidence. OCI archives remain private local build artifacts and are not uploaded or published.

The earlier source-9077736 scan correctly failed on new High libexpat CVE-2026-77214 in API/UI. Publisher refresh retained the same digests and the official package repository remained proxy-403-blocked. Source inspection plus supported APK removal of unused server font/image-filter packages removes their libexpat/libpng dependency chain; certificate/JRE/startup and financial/HTTPS/browser checks remain required. That failed scan was not suppressed. See [security rationale](PHASE5_SECURITY.md). Medium zlib is unresolved and must be followed up; a gate pass is not public-release approval. Refresh evidence before release, because these timestamps naturally expire.

## Local final rehearsal results

[Sanitized measurements/control/restore/rollback report](phase5-local-rehearsal.json), reviewed **18 October 2026 (Asia/Riyadh)**. Its runtime source/image IDs are explicitly recorded; later evidence-only commits may have different Git IDs. CI rebuilds and tests the delivered checkout independently.

| Check | Status | Actual result |
| --- | --- | --- |
| Backend regression | PASS | 91 tests, zero failures/errors/skips; all original 88 retained |
| Production HTTPS browser journeys | PASS | 12 passed in 1.8m on the reduced runtime; all original 10 retained, desktop/Pixel 7 emulation |
| Accessibility/presentation | PASS | Axe WCAG A/AA/2.1 AA including contrast; keyboard skip/tooltips/sliders/scroll regions; masked real synthetic screenshots |
| TypeScript/build/format | PASS | Strict TypeScript, Vite production build, Prettier, Java formatting, shell syntax and diff checks |
| Java/npm/negative release fixtures | PASS | 158 Java coordinates incl. tests/provided, zero advisories/query errors; npm zero; four negative gate tests |
| Complete proxy chain and container settings | PASS | 32 requests: 30 unauthorized then two throttled despite spoofing; distinct client succeeds; restart budget persists; exact image IDs/UIDs/caps/resources checked |
| DB outage probes | PASS | Readiness fails while liveness remains UP; isolated DB recovers |
| Bounded concurrent smoke | PASS | Four workers ×20 reads, zero errors, p95 **219.765 ms**, thresholds <1% errors / <1000ms |
| Encrypted new-database recovery | PASS | Nine financial-table fingerprints equal; SQL reconciliation; source unchanged; existing-target/ciphertext-integrity failures enforced; restore **2.835s**, local backup age **4.755s** at drill completion |
| Immutable-artifact transitions | PASS | Compatible V3 baseline→candidate→baseline→candidate, reauthentication/original key, exactly one execution and CONSISTENT reconciliation, no down migration/deletion |
| Final serving + rollback image gates | PASS | Zero Critical/High, one unresolved Medium zlib each; full sanitized evidence/SBOM/OCI artifacts generated |
| Actual target-host and off-host recovery | UNVERIFIED | No host/domain/storage/operator selected; local measured RTO is not target RTO or an observed offsite RPO |

The environment has two available CPU affinities, a 768 MiB API limit and 512 MiB database limit, sharing a managed cloud host. Timings are not production capacity/SLO claims. The backup schedule is twice daily with 24h RPO / 30m prepared-host RTO targets; no remote upload, retention validation or operator alert delivery occurred. Enrollment is closed again after the isolated test window, with a valid-CSRF 403 rejection checked separately. The source/history secret scan and final GitHub run/check links are recorded in the draft PR at delivery; passing local tests alone do not establish remote CI.

Draft delivery: [PR #5](https://github.com/Athbba/aegis-financial-risk/pull/5). Preserve draft status and the public-release block until missing target checks are explicitly authorized and verified.

An initial CI artifact scope mistakenly included image archives. Those two generated artifacts were deleted and the whitelist corrected to reports/SBOMs/checksum metadata/masked screenshots only. Images contained no runtime secrets/database records; no registry push or public service deployment occurred.
