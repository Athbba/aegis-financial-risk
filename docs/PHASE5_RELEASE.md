# Phase 5 release recommendation and blocker table

**Recommendation: BLOCKED for public release.** A target host/domain, real certificate operation, encrypted off-host destination and operator configuration are not selected. The user explicitly requested local preparation only. No deployment, upload, purchase, image publication or external infrastructure change is authorized or performed. A draft PR is the delivery artifact; do not merge or launch automatically.

Baseline: `b3151f55bf63709808b6279eb3970972db3162aa`, no later main changes at verification; [successful main CI](https://github.com/Athbba/aegis-financial-risk/actions/runs/39054875143). Pre-code plan: `083b6c0`. All statuses below distinguish local rehearsal from target deployment.

| Blocker/control | Status | Evidence | Remaining action |
| --- | --- | --- | --- |
| Verified merged Phase 4 baseline | PASS | Main CI above; [plan](PHASE5_PLAN.md) | Recheck main before merge |
| Final immutable image evidence, zero Critical/High, DB ≤48h | UNVERIFIED | [image policy](PHASE5_SECURITY.md); final scan manifest will be linked here | Finish final source-identified build/scan; refresh before release |
| Runtime role, membership/effective privilege rejection | PASS (local) | `DatabaseSafetyTest`, `DeploymentGuard`, [runbook](PHASE5_DEPLOYMENT.md) | Verify selected host/role configuration |
| Private API/DB, non-root initialization and container limits | PASS (local) | `rehearse-deployment-controls.py` effective inspections | Validate target filesystem/volume ownership and firewall |
| Trusted HTTPS, secure cookies, proxy client isolation | PASS (local) | Real trusted-CA stack/browser; separate client spoof/throttle/restart rehearsal | Domain/public certificate, canonical redirect, external proxy/firewall review |
| Closed enrollment and single-instance session/recovery policy | PASS (design); target UNVERIFIED | Default closed; explicit local test window; [runbook](PHASE5_DEPLOYMENT.md) | Select operator and supervised enrollment procedure |
| Encrypted fresh-database restore/source preservation | PASS (local) | `rehearse-encrypted-recovery.py`, nine table fingerprints and reconciliation | Off-host roundtrip, prepared target recovery timing |
| Scheduled protected off-host backup, retention, RPO | UNVERIFIED | Timer/service, encrypted upload/hash verification, failure/age checks provided | Choose destination/keys/account, authorize transfer, verify retention and observed age ≤24h |
| Upgrade/rollback, same-key recovery, retained trade | UNVERIFIED | `rehearse-upgrade.py` | Complete local run and target rehearsal |
| Readiness vs liveness during DB outage | PASS (local) | Real DB stop: readiness down, liveness UP | Configure target health/alert routing; don't restart solely for readiness |
| Concurrent smoke thresholds | PASS (local) | `deployment-smoke.py`: four clients ×80 total reads, <1% errors, p95<1s | Commit final measured report; target workload remains unverified |
| All regressions and negative release gates | UNVERIFIED (final commit) | Original 88/10 retained; 91 backend passed in initial run; added gate fixtures | Finish final suites and exact GitHub check URLs |
| Keyboard, contrast/mobile and synthetic screenshots | UNVERIFIED (final) | Axe/keyboard/mobile test added; scroll focus issue corrected | Final production bundle run and screenshots |
| Target host, certificate renewal, offsite restore, alert delivery | UNVERIFIED | User confirms none selected | Select/authorize/configure and test before public release |

The actual measured reports, immutable source/image summary and draft PR/check URLs are added at delivery. Historical Phase 4 reports remain historical; old clean Java results do not establish a clean current image or target environment. No production-readiness claim.
