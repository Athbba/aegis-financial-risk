#!/usr/bin/env bash
set -euo pipefail
: "${BACKUP_OUTPUT:?}" "${BACKUP_RECIPIENT:?}"
archive="$(/opt/aegis/scripts/encrypted-backup.sh "$BACKUP_OUTPUT" "$BACKUP_RECIPIENT" | tail -1)"
/opt/aegis/scripts/upload-backup.sh "$archive"
# Remote verified-copy retention is controlled at the destination, never deletion of source DB.
# Keep encrypted local copies for 14 days only AFTER transfer succeeds.
find "$BACKUP_OUTPUT" -maxdepth 1 -type f -name 'aegis-*.dump.gpg*' -mtime +14 -delete
