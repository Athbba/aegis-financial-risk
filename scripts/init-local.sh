#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -e .env ]]; then
  echo '.env already exists; preserved.'
  exit 0
fi
# Read 256 bits from the OS random source. Never print credentials or overwrite existing files.
local_password="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
(umask 077; set -o noclobber; printf 'DATABASE_PASSWORD=%s\nCOOKIE_SECURE=false\n' "$local_password" > .env)
unset local_password
echo 'Created .env with a random local database password.'
