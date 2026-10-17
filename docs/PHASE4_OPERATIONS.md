# Phase 4 operations and recovery

The default Compose profile remains compatible with the existing local database and ignored `.env`. It is a local development convenience, **not** a least-privilege deployment. Existing volumes/credentials are preserved. No phase-4 recovery test uses that active volume. Production readiness, public deployment, multi-instance sessions and a managed monitoring service are not claimed.

## Separate migration and serving privileges

1. Back up the database first to a private directory. Keep the current owner credential for trusted migrations only.
2. Build the jar and run a **short-lived process** with `MIGRATION_DATABASE_URL`, `MIGRATION_USERNAME`, `MIGRATION_PASSWORD` supplied privately:

   ```bash
   java -jar backend/target/aegis-0.1.0.jar --migrate-only
   ```

   This process applies Flyway and seeds the deterministic market data transactionally, then exits without starting HTTP, sessions or JPA. It requires no application runtime password. Do not put credentials in arguments, URLs or shell history.
3. `./scripts/init-runtime-env.sh` creates a separate ignored, mode-0600 `.env.runtime` with a random runtime password, preserving any existing file. It does not modify the database. Load those **trusted local** variables into your shell without printing them, and run `psql -f ops/runtime-role.sql` as the migration/owner role, with standard PG connection variables and a private `PGPASSFILE`. psql reads the runtime password from its environment; it is not passed on the command line. Existing runtime role passwords are preserved; use an explicit operator rotation procedure if the role already exists with different credentials.
4. Start the existing services with the optional override:

   ```bash
   docker compose --env-file .env --env-file .env.runtime \
     -f compose.yml -f compose.prebuilt.yml -f compose.least-privilege.yml up -d
   ```

   Runtime Flyway and market seeding are disabled. No migration credentials are passed to the serving container. Reapply reviewed grants after a future migration introduces tables/columns. The override does not create/drop/restore a database or change the active volume.

The role script refuses an owner/admin role and restricts names to dedicated `aegis_runtime...` roles. It grants SELECT, required INSERTs, updates only to portfolio cash/date and lot remaining quantity/cost, short-lived authentication-window updates/deletes and sequence use. It grants no schema creation, price mutation, account mutation, immutable-history mutation, trigger disabling or role administration. Hibernate updates only dirty mutable columns. PostgreSQL tests actually run registration, login, buy, partial FIFO sell and reconciliation using these grants, then prove forbidden statements fail.

These are **application-wide table privileges**, not row-level security: ownership remains enforced in each API/service. A compromised runtime process could still read application tables and append records using its grants. Trusted database owners can alter/drop objects; immutable triggers do not provide cryptographic tamper resistance.

## Backup and disposable restore

Requires PostgreSQL client tools of an appropriate version (tested with PostgreSQL 17). Configure `PGHOST`, `PGPORT`, `PGUSER`, `PGDATABASE` and a mode-0600 `PGPASSFILE` outside Git. Use a private backup directory. Passwords never belong in script arguments.

```bash
./scripts/backup-db.sh /private/backup/aegis.dump
./scripts/restore-disposable-db.sh /private/backup/aegis.dump aegis_restore_drill_unique
```

Backup uses custom format, no owner/privilege restoration, mode-0600 output, and a SHA-256 manifest. Restore checks the manifest **before** creating a database. It accepts only a new `aegis_restore_...` name, refuses the source/existing database, and never issues DROP/--clean. A failed restore leaves the disposable database for inspection; no active state is replaced. The checksum detects accidental corruption, not malicious modification of both files, so protect the dump/manifest and encrypt off-host backups according to deployment needs.

`DatabaseSafetyTest` creates generated simulated trades in isolated Testcontainers PostgreSQL, invokes these actual scripts, restores a separate database and compares ordered row fingerprints for users, portfolios, assets, prices, orders, lots, allocations, ledger and audit. It verifies the original source is unchanged and refuses destructive targets. Another isolated V2 fixture is upgraded to V3 with unchanged financial fingerprints. This is a reproducible drill, not a point-in-time recovery/PITR, retention, offsite backup or disaster-recovery certification. Never delete the user's volume to run it.

## Reconciliation and audit visibility

`GET /api/portfolios/{id}/reconciliation` is authenticated and owner-scoped, read-only and repeatable-read. It compares current cash to immutable executions and ledger totals, current quantities/FIFO cost to reconstructed history, and each execution's ledger/audit references. It returns named PASS/FAIL checks and CONSISTENT/INCONSISTENT, never repairs records. Inconsistent immutable accounting can instead fail the existing accounting invariant checks. The existing owner-scoped, paginated `/audit` endpoint remains the durable event view.

Limits are the existing 10,000 execution/100 asset caps and 15-second analytics timeout. This is a targeted set of invariants, not proof against every possible owner-level database alteration. Diagnose mismatches, preserve evidence and restore/repair through a separately reviewed process; never rewrite immutable records to make a check green.

## Health, metrics and troubleshooting

- Public `/actuator/health` and `/actuator/health/readiness` include database availability, with no component/credential details. `/actuator/health/liveness` checks application liveness independently of database availability. A database outage should remove readiness, not trigger repeated liveness restarts.
- `/actuator/metrics` and `/actuator/metrics/{name}` require a separate stateless HTTP Basic identity, username `operator`, using `AEGIS_OPERATIONS_PASSWORD_HASH` (BCrypt). An empty hash disables operator login; normal user sessions cannot access metrics. Other Actuator endpoints are not exposed. Use HTTPS or a private trusted tunnel; Basic credentials are not transport encryption. Generate hashes offline with a trusted BCrypt tool and keep configuration outside Git. Application users cannot self-register an OPS role.
- `aegis.requests`: duration/count by bounded route template, method and success/rejected/infrastructure_failure. Framework `http.server.requests`, JVM/process and Hikari metrics remain available through Actuator.
- `aegis.trades`: count by BUY/SELL and committed/replayed/rejected/rolled_back. Counters and completion logs run after transaction completion, so a processed request is not mislabeled as committed. Business rejection is distinct from infrastructure rollback. Metrics are process-local and reset at restart; durable execution/audit rows remain authoritative.
- Every response gets a server-generated `X-Request-ID`; safe problem responses include that ID. JSON logs include it, HTTP method, route template, status/outcome, and safe failure type. Logs omit raw path/query, bodies, passwords, email/IP, cookies, tokens and session IDs. Untrusted request-ID headers are ignored. Hibernate's raw SQL-error-detail logger is disabled; application failure logging does not print exception messages or bound values. Do not enable body/access/debug logging without a redaction review.
- To debug an uncertain trade: keep its original key; use owner-scoped `/orders/by-key/{key}`, then retry exactly the saved request if no committed result is visible. A 404 alone does not prove an in-flight transaction cannot commit. Browser refresh/login recovery preserves the tab's intent. Closing the tab clears sessionStorage; clearing browser storage requires manual history review. Never automatically retry with a new key.

Containers run without root, with all capabilities dropped, no-new-privileges, read-only filesystems, bounded writable tmpfs and process limits. The API retains its 15-second transaction timeout and uses a 5-second connection-pool acquisition limit, 2-second connection validation and graceful 20-second shutdown. These controls reduce exposure; they are not a substitute for HTTPS, OS patching, backups, rate-limit tuning and an independent security review.
