# Rebuilding the Codex cloud workspace

These instructions preserve the environment draft's installation and startup contents alongside Phase 2 source. They contain no credential values. The draft was saved during onboarding; saving a draft does not publish a filesystem snapshot or prove restoration in a new task. Review/save the configuration in environment settings, then publish to retain the prepared environment.

The cloud checkout path is `/workspace/aegis-financial-risk`. For normal developer machines, follow the portable local commands in [README.md](../README.md). No Git worktree is needed; each Codex cloud task is already isolated.

## Installation script

The following was saved as `install_script`. It prepares the JDK/Maven, preserves or creates local-only credentials, installs locked frontend dependencies, runs backend verification, builds the UI and packages runtime images. Docker must be available. No paid API keys are needed.

```bash
#!/usr/bin/env bash
set -euo pipefail
cd /workspace/aegis-financial-risk
./scripts/cloud-bootstrap.sh
./scripts/init-local.sh
./scripts/cloud-maven.sh -B -ntp verify
npm --prefix frontend ci --cache /workspace/.cache/npm
npm --prefix frontend run build
BUILDX_CONFIG=/workspace/.cache/buildx docker compose -f compose.yml -f compose.prebuilt.yml build
```

## Startup instructions

The following was saved as `start_skill`. Retained files and dependencies differ from live processes and sessions, which must restart.

Use the existing checkout at /workspace/aegis-financial-risk. Each cloud task is already isolated; do not create a Git worktree unless the user explicitly requests one. Preserve user changes, the ignored .env file and existing database volume.

Retained files include the full JDK at /workspace/.tools/jdk-21, Maven at /workspace/.tools/apache-maven-3.9.11, dependency caches, the built backend jar, frontend/dist and local runtime images. Live application processes and sessions must be restarted after environment restoration; do not assume they survive. scripts/cloud-maven.sh activates the prepared JDK, writable Maven cache, existing egress proxy and system trust store without disabling verification. scripts/cloud-bootstrap.sh can prepare the tooling again if needed.

From /workspace/aegis-financial-risk:
1. Run ./scripts/init-local.sh if .env is missing. It creates a random local-only DB password without printing it. Do not overwrite existing credentials or erase database volumes. If retained DB credentials no longer match, diagnose the binding rather than deleting data.
2. After source/dependency changes, rebuild with ./scripts/cloud-maven.sh -B -ntp verify, npm --prefix frontend ci --cache /workspace/.cache/npm, and npm --prefix frontend run build.
3. Start/package the current artifacts with BUILDX_CONFIG=/workspace/.cache/buildx docker compose -f compose.yml -f compose.prebuilt.yml up --build -d. This path uses locally verified artifacts because Maven DNS resolution in source-build containers failed in this cloud environment. Stop only conflicting Java/Vite services started for this repository when necessary.
4. Run ./scripts/wait-ready.sh. It requires backend/database health UP and the expected frontend HTML. Inspect docker compose ps -a and docker compose logs --tail=100 on failure.
5. For changes affecting the user journey, run PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium npm --prefix frontend run test:e2e. Eight desktop/mobile checks passed with installed system Chromium; managed browser downloads were denied. The journeys exercise registration, funding, buy/sell, rejection, history, dated valuation, missing prices, returns, chart interaction and retry against the real stack. Health alone does not establish financial correctness.

The local UI listens on 5173, backend on 8080, PostgreSQL on 5432, all bound to loopback. Do not produce localhost preview links in onboarding. Register an account through the UI; no shared login exists. This is educational simulated investing only: all ALTA/BRIM/COVE prices are fixed-seed synthetic USD data. No real trades, brokerage connections or paid credentials are needed. Phase 3 is implemented; do not begin Phase 4 without the user's request. See README.md, TESTING.md, FINANCIAL_ASSUMPTIONS.md, PROGRESS.md and docs/MENTORING.md. Public deployment and fresh-task restoration have not been verified. GitHub Actions results must be checked for the current commit; see TESTING.md and the delivery pull request.

## GitHub access

Native Git uses the platform's existing HTTPS authentication/proxy. Git read/push and GitHub API access are separate capabilities. Pull-request creation additionally needs network access to `api.github.com` and GitHub authorization. The API domain was added to the environment draft during repository preservation; draft saves do not apply runtime changes. Preserve existing network presets/custom rules when reviewing that addition. Never copy tokens into this document or the repository.

## Files to preserve

- Repository: source, migrations, dependency manifests/lockfile, Docker/Compose, CI, tests and documentation.
- Helper scripts: `scripts/cloud-bootstrap.sh`, `scripts/cloud-maven.sh`, `scripts/init-local.sh`, `scripts/start-backend.sh`, `scripts/wait-ready.sh`.
- Local credentials: `.env` is intentionally ignored by Git. Generate a new local configuration on a fresh database; preserve matching credentials for existing database volumes. Never commit or paste secret values.
- Database: the Compose named volume is local state, not part of the Git repository. GitHub source preservation is not a backup of local users or simulated transactions. Use PostgreSQL backups separately if retaining that local data is required.

AEGIS uses only deterministic fictional asset prices and simulated investing. Phase 2 uses a persisted 2022-01-03 funding baseline and end-of-day immutable reconstruction; V2 upgrades existing portfolios without rewriting their history. Do not begin Phase 4 without the user's request.

Phase 3 adds risk endpoints and a Risk Lab tab, without migrations or new runtime services. Dependency overrides and remaining advisory limitations are recorded in [PHASE3_SECURITY.md](PHASE3_SECURITY.md). Rebuild both artifacts after source changes, then run all backend/browser checks.
