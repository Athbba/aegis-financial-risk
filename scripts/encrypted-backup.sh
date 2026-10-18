#!/usr/bin/env bash
# Local encryption only. Remote transfer is a separate authorized operator action.
set -euo pipefail
[[ $# == 2 && "$2" =~ ^[A-Fa-f0-9]{40}$ ]] || { echo 'Usage: BACKUP_GNUPGHOME=/private/keyring encrypted-backup.sh PRIVATE_OUTPUT RECIPIENT_FINGERPRINT' >&2; exit 2; }
: "${BACKUP_GNUPGHOME:?Configure verified recipient keyring}"
[[ -d "$1" && $(stat -c %a "$1") == 700 ]] || { echo 'Private output directory must be mode 0700.' >&2; exit 2; }
umask 077
repo="$(cd "$(dirname "$0")/.." && pwd)"
working="$(mktemp -d "$1/.backup.XXXXXX")"
trap 'rm -rf "$working"' EXIT
start="$SECONDS"
"$repo/scripts/backup-db.sh" "$working/aegis.dump"
name="aegis-$(date -u +%Y%m%dT%H%M%SZ).dump.gpg"
[[ ! -e "$1/$name" ]] || exit 2
gpg --batch --homedir "$BACKUP_GNUPGHOME" --recipient "$2" --output "$working/$name" --encrypt "$working/aegis.dump"
# Publish only complete encrypted objects; plaintext private scratch is removed on all exits.
mv "$working/$name" "$1/$name"
sha256sum "$1/$name" > "$1/$name.sha256"
printf '{"status":"LOCAL_ENCRYPTION_COMPLETE","createdAt":"%s","elapsedSeconds":%s,"remoteVerified":false}\n' "$(date -u +%FT%TZ)" "$((SECONDS-start))" > "$1/last-backup.json"
echo "$1/$name"
