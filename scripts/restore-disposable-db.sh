#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 2 || ! "$2" =~ ^aegis_restore_[a-z0-9_]+$ || -z "${PGUSER:-}" ]]; then
  echo 'Usage: PGUSER=... PGPASSFILE=/private/path restore-disposable-db.sh backup.dump aegis_restore_unique_name' >&2
  exit 1
fi
# createdb refuses an existing database. No drop, replace, --clean or active-volume operation.
restore_target="$2"
if [[ "$restore_target" == "${PGDATABASE:-}" ]]; then echo 'Restore target must differ from source database.' >&2; exit 1; fi
if [[ ! -f "$1.sha256" ]]; then echo 'A SHA-256 backup manifest is required.' >&2; exit 1; fi
read -r expected_hash _ < "$1.sha256"
actual_hash="$(sha256sum "$1")"
if [[ ! "$expected_hash" =~ ^[a-f0-9]{64}$ || "${actual_hash%% *}" != "$expected_hash" ]]; then echo 'Backup checksum mismatch.' >&2; exit 1; fi
createdb -- "$restore_target"
pg_restore --exit-on-error --no-owner --no-privileges --dbname="$restore_target" -- "$1"
echo 'Restored into the new disposable database. Run reconciliation and row-count/hash checks before considering recovery.'
