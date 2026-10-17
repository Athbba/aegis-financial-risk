# Phase 5: one-host deployment preparation

**No target host, hosting provider, domain or off-host backup destination has been selected. No public deployment or backup upload is authorized or performed.** This runbook describes a prepared configuration and local rehearsal, not a verified target environment. See [release blockers](PHASE5_RELEASE.md).

## Topology and privileges

Use `compose.deployment.yml` by itself, never layered over the existing local Compose project. One HTTPS Nginx edge serves React and proxies `/api/` to one Java API. Database/API/metrics have no host ports; the backend/database use an internal Docker network. Only loopback HTTP 8085 and HTTPS 8443 are published in rehearsal. Do not change these bindings or point this configuration at an existing live database without separate authorization.

1. PostgreSQL initializes **as uid 70 (`postgres`)**, then serves with writable data and runtime directories. Docker initializes a **new** named data volume from the publisher's postgres-owned path. It has a writable root filesystem, 512 MiB memory and 256 PIDs, dropped capabilities and no-new-privileges. It is not claimed to be fully read-only. Existing incompatible volume ownership must be handled by a separately reviewed migration, never an automatic recursive chown or volume deletion.
2. Short-lived `migrate` reads the owner secret, applies existing Flyway migrations/seeds and exits without a serving web process. `provision` reads owner/runtime secrets, checks the role and grants exact required privileges, then exits.
3. The serving API receives **only** the runtime password and operations hash through Spring config-tree files. It receives no owner/migration secret or credential environment variable. Flyway and seeding are off; secure cookies are on. `DeploymentGuard` refuses unsafe settings and effective role privileges. The optional development profile remains unchanged and is not a deployment substitute.
4. API uid 100 / UI uid 101, read-only roots, bounded writable tmpfs, capabilities dropped, no-new-privileges, CPU/memory/PID limits. `validate-deployment.py` requires immutable local image IDs and the exact private proxy address; the real rehearsal additionally inspects effective container settings.

`ops/runtime-role.sql` rejects administrator flags, **any** role membership (including NOINHERIT membership usable through SET ROLE), database/schema/relation/function ownership, CREATE/TEMP, excess effective table/column rights including PUBLIC-derived updates, grant options and writes outside application tables. It fails with a real SQL error under `ON_ERROR_STOP`; PostgreSQL 17's `\quit 1` does not establish a failing process status. Negative tests retain auth/BUY/partial FIFO SELL/reconciliation and deny DDL, trigger disabling, price changes and immutable UPDATE/DELETE. This is application-wide table authorization, not row-level security or protection against a trusted database administrator.

## Local reproduction

Requires Java 21/Maven, Docker Compose, Node, Python, OpenSSL, GnuPG, `certutil`, installed Playwright Chromium, verified Trivy/Gitleaks and space for OCI artifacts. The scripts intentionally generate **new** secrets and a new database project; they refuse existing secret directories. The existing `.env`, credentials, financial records and original volumes are not used.

```bash
./scripts/cloud-maven.sh -B -ntp verify
npm --prefix frontend ci
npm --prefix frontend run build
npm --prefix frontend run format:check
npm --prefix frontend audit
python3 -m unittest discover -s scripts/tests
./scripts/install-release-scanners.sh .tools/scanners
# Portable CI path uses mvn and its managed Chromium. Pick a NEW private path/project.
AEGIS_REHEARSAL_PROJECT=aegis-p5-example TRIVY="$PWD/.tools/scanners/trivy" \
  ./scripts/ci-deployment.sh /private/new-rehearsal /private/safe-evidence
```

The complete runner additionally builds the exact Phase 4 Java baseline using portable `mvn`; the cloud helper can prepare that baseline with the same proxy/JDK/cache when required. Cloud Chromium needs write access to its NSS database; importing a CA into a read-only database is insufficient. This is a filesystem permission requirement, not a reason to disable TLS. `NODE_EXTRA_CA_CERTS` supplies the same CA to Playwright's Node request client. Rehearsal CA/private keys expire quickly and never leave private scratch; remove the temporary browser trust anchor when finished. Never upload traces, cookie jars, private directories, dumps or generated keys.

Docker subnets are selected from unused ranges for isolated rehearsals. The trusted address is the edge's **exact private IPv4 address**, not a broad forwarded-header trust rule. One isolated project runs at a time per selected subnet/ports. The design intentionally uses IPv4 for canonical client identity; adding IPv6 is a separate tested boundary change.

## HTTPS, proxy and enrollment

`deploy/https.conf` is the tested loopback rehearsal edge. It redirects HTTP to HTTPS on 8443; the certificate has localhost/frontend SANs and is signed by an explicitly trusted disposable CA. No browser/curl insecure option is used. TLS 1.2/1.3, CSP, HSTS, referrer/permissions/content-type headers and secure HttpOnly/SameSite session/CSRF cookies are checked. `/actuator/` is denied at the edge; use protected metrics on the private network.

Before an authorized target deployment, obtain a domain and public certificate using a trusted ACME client, configure the canonical domain/443 redirect and firewall, and validate the entire target proxy chain. Keep private keys in a mode-0700 parent directory with controlled readable file mounts; Compose bind-file secrets do not implement an encrypted secret store or reliably change file ownership. Rehearsal secret files have read permission for distinct container UIDs inside a private host parent. Do not put passwords or private keys in images, Git, CLI arguments or resolved environment logs.

Renewal: configure the chosen ACME client's timer, test against its staging endpoint with normal certificate validation, verify SAN/chain/expiry, run `nginx -t`, atomically replace the certificate/key and reload Nginx, then verify externally. Alert below 14 days to expiry. No domain/ACME account exists here, so issuance/renewal/alert delivery are **UNVERIFIED**. Rotate database credentials with a short maintenance window and verified secret-file replacement/recreation; reauthentication after API restart is expected. Never overwrite an existing credential file automatically.

The edge discards external `Forwarded`, `X-Forwarded-For`, `X-Real-IP` and replaces `X-Aegis-Client-IP` with the transport client address. Only that exact private edge peer may supply a single canonical IPv4 value. Other peers' headers are ignored. Account limits remain 5/15m; login clients 30/5m; registrations 30/hour. Distinct edge clients are tested through separate containers, including spoofing and restart. Expiry is tested with a controlled clock in real PostgreSQL, not a shortened deployment throttle. NAT users still share an address; this is not botnet protection.

Enrollment defaults **closed**. It is open only during the loopback automated rehearsal and must be closed again afterward. An operator may explicitly open a short supervised synthetic demo enrollment window after target authorization; client/account limits still apply. Email verification and password recovery do not exist and are not claimed. Existing single-JVM sessions disappear on restart/rollback; the user signs in again. Tab-local unresolved order payload/key remains and exact-key lookup/retry recovers confirmation without another execution. Clearing/closing the tab still loses that journal.

## Backups, recovery and operations

Declared demo targets: **RPO 24 hours, RTO 30 minutes**, measured from backup availability to restored validation on a prepared host; replacement-host procurement and unknown offsite transfer times are not certified. The timer runs every 12 hours, giving margin under the RPO. Plaintext exists only in private temporary scratch; GnuPG encrypts to an operator-verified recipient fingerprint. Keep the private decryption key offline with a tested recovery custodian; the scheduled host needs only its trusted recipient public key. SSH uses pinned independently verified known-host keys, BatchMode and authenticated transport. Upload requires explicit `AEGIS_REMOTE_BACKUP_AUTHORIZED=yes`; none was enabled here.

Templates: `deploy/aegis-backup.{service,timer}`, `scheduled-backup.sh` and `aegis-alert@.service`. Before installation, select the host/service account, read-only backup DB credential, PostgreSQL-client connection over the private network, verified keyring, SSH identity/known-host file, private mode-0700 spool and **different-host** destination. A host systemd account cannot resolve a Docker-only database hostname by itself: supply an approved short-lived private-network client/container adapter (as the local rehearsal does), or a protected local Unix-socket arrangement. Do not publish the database port just to make scheduling work. Docker-socket access is host-administrative authority; do not quietly give it to the serving API or call an unconfigured backup account least-privilege.

`encrypted-backup.sh` produces encrypted objects and checksums. `upload-backup.sh` transfers only encrypted files, independently reads the remote ciphertext hash through pinned SSH, and marks remote verification only on equality; it does not certify remote retention or restoration. Target storage must have a dedicated nonshared identity, encryption, a private directory, independently enforced **at least 14-day** retention/versioning and deletion protection against API compromise. Local retention deletes only old encrypted spool files after a successful upload. Provider/server lifecycle enforcement, permissions, schedule enablement, an actual remote restore and failure notifications remain **pending configuration and verification**. No remote command, provisioning or transfer was run.

Local encrypted roundtrip uses the actual pg_dump/backup/restore scripts, a new `aegis_restore_*` database, nine complete financial-table fingerprints, SQL cash/FIFO/ledger/audit reconciliation, encrypted integrity failure and refusal to restore into an existing database. Source fingerprints remain equal. Plaintext/key scratch is removed after the drill; the new disposable restored database is left for inspection. The existing database is never dropped/restored or down-migrated.

Alerts: liveness HTTP failure is a process incident; readiness/database failure removes serving readiness without automatic liveness restart. Run `check-operations.py` against observed protected API error counts, disk free (alert <15%) and backup state (alert absent/unverified/>24h). API errors ≥1% across ≥100 observed requests alert; do not confuse expected business rejections with infrastructure errors. Journal `OnFailure` supplies local failure visibility; actual operator routing, alert evaluation cadence (recommend 5 minutes), synthetic alert delivery and remote backup-age observations require the selected target. Metrics reset on process restart. Logs and artifacts must exclude credentials, session identifiers and financial records.

Upgrade/rollback uses immutable image IDs, compatible V3 schema and the same volume, never deletion/down-migration. The rehearsal repackages exact Phase 4 Java source on the supported runtime, commits a trade, upgrades, reauthenticates and recovers/replays its original key, rolls back and verifies again, then returns to the candidate with exactly one execution and CONSISTENT reconciliation. Future schema migrations must retain backward compatibility during the rollback window; a passing V3 rehearsal does not authorize arbitrary down migrations.
