#!/usr/bin/env bash
# Operator-only: no remote transfer occurs without explicit external authorization/configuration.
set -euo pipefail
[[ "${AEGIS_REMOTE_BACKUP_AUTHORIZED:-}" == yes ]] || { echo 'Remote backup is not authorized/configured.' >&2; exit 2; }
[[ $# == 1 && "$1" == *.dump.gpg && -f "$1.sha256" ]] || exit 2
: "${BACKUP_REMOTE:?Set user@host:/absolute/directory}"
: "${BACKUP_KNOWN_HOSTS:?Pin independently verified SSH host keys}"
[[ "$BACKUP_REMOTE" =~ ^[a-z_][a-z0-9_-]*@[a-zA-Z0-9.-]+:/[a-zA-Z0-9_/-]+$ ]] || exit 2
[[ -s "$BACKUP_KNOWN_HOSTS" ]] || exit 2
# rsync/ssh must fail if authentication or host trust is absent. No accept-new/disabled trust.
rsync --checksum --protect-args -e "ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$BACKUP_KNOWN_HOSTS" -- "$1" "$1.sha256" "$BACKUP_REMOTE/"
# Independently read the remote ciphertext hash through the same pinned SSH identity.
backup_name="$(basename "$1")"
[[ "$backup_name" =~ ^aegis-[0-9]{8}T[0-9]{6}Z\.dump\.gpg$ ]] || exit 2
remote_host="${BACKUP_REMOTE%%:*}"; remote_directory="${BACKUP_REMOTE#*:}"
remote_hash="$(ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$BACKUP_KNOWN_HOSTS" "$remote_host" "sha256sum -- $remote_directory/$backup_name")"
read -r expected_hash _ < "$1.sha256"
[[ "${remote_hash%% *}" == "$expected_hash" ]] || { echo 'Remote encrypted backup checksum mismatch.' >&2; exit 1; }
python3 - "$(dirname "$1")/last-backup.json" <<'PYCODE'
import datetime,json,pathlib,sys
p=pathlib.Path(sys.argv[1]);d=json.loads(p.read_text());d.update(remoteVerified=True,remoteVerifiedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),restoreVerified=False);p.write_text(json.dumps(d)+'\n')
PYCODE
printf '{"status":"REMOTE_CIPHERTEXT_HASH_VERIFIED","restoreVerified":false}\n'
