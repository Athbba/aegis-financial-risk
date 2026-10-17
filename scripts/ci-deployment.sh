#!/usr/bin/env bash
# Fully local isolated rehearsal. Never publishes an image or transfers a backup.
set -euo pipefail
[[ $# == 2 ]] || { echo 'Usage: ci-deployment.sh PRIVATE_REHEARSAL_DIR SAFE_EVIDENCE_DIR' >&2; exit 2; }
repo="$(cd "$(dirname "$0")/.." && pwd)"; cd "$repo"
private="$1"; evidence="$2"; mkdir -p "$evidence"
python3 scripts/init-deployment-rehearsal.py "$private"
revision="$(git rev-parse HEAD)"
docker build --build-arg SOURCE_COMMIT="$revision" --target prebuilt -t aegis-p5-api:rehearsal backend
docker build --build-arg SOURCE_COMMIT="$revision" --target prebuilt -t aegis-p5-ui:rehearsal frontend
docker build --build-arg SOURCE_COMMIT="$revision" -f deploy/database.Dockerfile -t aegis-p5-db:rehearsal .
python3 - "$private/deployment.env" <<'PY'
import pathlib,subprocess,sys
p=pathlib.Path(sys.argv[1]);lines=p.read_text().splitlines()
for key,name in [('API_IMAGE','api'),('UI_IMAGE','ui'),('DATABASE_IMAGE','db')]:
 value=subprocess.check_output(['docker','image','inspect','aegis-p5-'+name+':rehearsal','--format','{{.Id}}'],text=True).strip()
 lines=[key+'='+value if l.startswith(key+'=') else l for l in lines]
p.write_text('\n'.join(lines)+'\n')
PY
export COMPOSE_FILE="$repo/compose.deployment.yml" COMPOSE_ENV_FILES="$private/deployment.env"
docker compose config --format json | python3 scripts/validate-deployment.py > "$evidence/configuration.json"
docker compose up -d
for attempt in $(seq 1 90); do
  if docker compose exec -T backend wget -Y off -qO- http://127.0.0.1:8080/actuator/health/readiness >/dev/null 2>&1 && curl --fail --silent --cacert "$private/ca-cert" https://localhost:8443/api/auth/csrf >/dev/null; then break; fi
  [[ "$attempt" != 90 ]] || exit 1
  sleep 1
done
# This installs a disposable trust anchor, not an ignore-certificate-errors option.
mkdir -p "$HOME/.pki/nssdb"
if [[ ! -f "$HOME/.pki/nssdb/cert9.db" ]]; then certutil -N --empty-password -d "sql:$HOME/.pki/nssdb"; fi
certutil -A -d "sql:$HOME/.pki/nssdb" -n aegis-phase5-rehearsal -t 'C,,' -i "$private/ca-cert"
export NODE_EXTRA_CA_CERTS="$private/ca-cert" E2E_BASE_URL=https://localhost:8443 AEGIS_E2E_RESTART_BACKEND=true AEGIS_E2E_PRIVATE_HEALTH=true
npm --prefix frontend run test:e2e
# Copy only deliberately captured masked synthetic screenshots; never trace/report/session artifacts.
find frontend/test-results -name phase5-synthetic-demo.png -exec sh -c 'name="$(basename "$(dirname "$1")")"; cp "$1" "$2/$name.png"' sh {} "$evidence" \;
python3 scripts/deployment-smoke.py --ca "$private/ca-cert" --output "$evidence/load.json"
python3 scripts/rehearse-deployment-controls.py --env-file "$private/deployment.env" --ca "$private/ca-cert" --output "$evidence/controls.json"
python3 scripts/rehearse-encrypted-recovery.py --env-file "$private/deployment.env" --output "$evidence/recovery.json"
# Build the exact verified Phase 4 Java source on the same supported runtime for local rollback.
baseline="$(mktemp -d /tmp/aegis-p5-baseline.XXXXXX)"
git archive b3151f55bf63709808b6279eb3970972db3162aa backend | tar -x -C "$baseline"
mvn -B -ntp -f "$baseline/backend/pom.xml" -DskipTests package
cp backend/Dockerfile "$baseline/backend/Dockerfile"
docker build --target prebuilt --build-arg SOURCE_COMMIT=b3151f55bf63709808b6279eb3970972db3162aa -t aegis-p5-api:baseline "$baseline/backend"
python3 scripts/rehearse-upgrade.py --env-file "$private/deployment.env" --ca "$private/ca-cert" --baseline-image "$(docker image inspect aegis-p5-api:baseline --format '{{.Id}}')" --output "$evidence/upgrade.json"
# Enrollment is open ONLY for the isolated test window, then explicitly closed.
sed -i 's/DEMO_ENROLLMENT=open/DEMO_ENROLLMENT=closed/' "$private/deployment.env"
docker compose up -d --no-deps backend
for attempt in $(seq 1 90); do
  if docker compose exec -T backend wget -Y off -qO- http://127.0.0.1:8080/actuator/health/readiness >/dev/null 2>&1; then break; fi
  [[ "$attempt" != 90 ]] || exit 1
  sleep 1
done
docker compose restart frontend
sleep 1
python3 scripts/verify-closed-enrollment.py "$private/ca-cert" > "$evidence/enrollment.json"
docker compose config --format json | python3 scripts/validate-deployment.py > "$evidence/configuration-closed.json"
scripts/scan-release-images.sh "$evidence" aegis-p5-api:rehearsal aegis-p5-ui:rehearsal aegis-p5-db:rehearsal
