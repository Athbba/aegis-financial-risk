#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -e .env.runtime ]]; then echo '.env.runtime already exists; preserved.'; exit 0; fi
runtime_password="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
(umask 077; set -o noclobber; printf 'AEGIS_RUNTIME_USER=aegis_runtime\nAEGIS_RUNTIME_PASSWORD=%s\n' "$runtime_password" > .env.runtime)
unset runtime_password
echo 'Created private .env.runtime with independent credentials; no database changes made.'
