#!/usr/bin/env bash
# Uses standard PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSFILE, never a password argument.
set -euo pipefail
if [[ $# != 1 || -z "${PGDATABASE:-}" || -z "${PGUSER:-}" ]]; then
  echo 'Usage: PGDATABASE=... PGUSER=... PGPASSFILE=/private/path backup-db.sh /private/path/backup.dump' >&2
  exit 1
fi
umask 077
if [[ -e "$1" ]]; then echo 'Refusing to overwrite an existing backup.' >&2; exit 1; fi
pg_dump --format=custom --no-owner --no-privileges --file="$1"
sha256sum "$1" > "$1.sha256"
echo 'Backup and SHA-256 manifest written. Treat both as private operational files.'
