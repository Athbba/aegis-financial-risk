#!/usr/bin/env bash
set -euo pipefail
for attempt in $(seq 1 60); do
  if curl --fail --silent http://localhost:8080/actuator/health | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'  &&
     curl --fail --silent http://localhost:5173/ | grep -q 'AEGIS'; then
    echo 'Backend/database health UP; frontend HTML served. Use browser tests to verify the user journey.'
    exit 0
  fi
  sleep 1
done
echo 'Readiness timed out. Inspect docker compose ps -a and docker compose logs.' >&2
exit 1
