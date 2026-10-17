#!/usr/bin/env bash
# Usage: TRIVY=... scripts/scan-release-images.sh OUTPUT API UI DATABASE
set -euo pipefail
[[ $# == 4 ]] || exit 2
output="$1"; shift
mkdir -p "$output"
scanner="${TRIVY:-trivy}"
# A refresh error is a gate failure, even if an older cache exists. No skip-update fallback.
"$scanner" image --download-db-only --db-repository ghcr.io/aquasecurity/trivy-db:2 > "$output/scanner.log" 2>&1
"$scanner" version --format json > "$output/scanner-version.json"
repo="$(cd "$(dirname "$0")/.." && pwd)"
images=("$@")
index=0
for name in api ui database; do
  image="${images[$index]}"; index=$((index+1))
  docker image inspect "$image" --format '{{json .}}' > "$output/$name-identity.private.json"
  "$scanner" image --skip-db-update --java-db-repository ghcr.io/aquasecurity/trivy-java-db:1 --scanners vuln --format json --output "$output/$name-scan.json" "$image" >> "$output/scanner.log" 2>&1
  "$scanner" image --skip-db-update --format cyclonedx --output "$output/$name-sbom.json" "$image" >> "$output/scanner.log" 2>&1
  python3 "$repo/scripts/oci-export.py" "$image" "$output/$name-oci.tar"
done
"$scanner" version --format json > "$output/scanner-version.json"
python3 - "$output" "$repo" <<'PY'
import hashlib,json,pathlib,subprocess,sys
out=pathlib.Path(sys.argv[1]); version=json.loads((out/'scanner-version.json').read_text())
manifest={'scanner':'Trivy','scannerVersion':version['Version'],'database':version['VulnerabilityDB'],'javaDatabase':version.get('JavaDB'),'sourceCommit':subprocess.check_output(['git','-C',sys.argv[2],'rev-parse','HEAD'],text=True).strip(),'images':{}}
for name in ['api','ui','database']:
 identity=json.loads((out/(name+'-identity.private.json')).read_text())
 scanpath=out/(name+'-scan.json'); scan=json.loads(scanpath.read_text())
 # Full sanitized scanner report: remove image environment/history (may include build/proxy values).
 config=scan.get('Metadata',{}).get('ImageConfig',{})
 config.pop('history',None)
 for block in [config,config.get('config',{}),config.get('container_config',{})]:
  block.pop('Env',None);block.pop('env',None)
 scanpath.write_text(json.dumps(scan,indent=2)+'\n')
 manifest['images'][name]={'imageId':identity['Id'],'platform':identity['Os']+'/'+identity['Architecture'],'publisherDigests':identity.get('RepoDigests',[]),'sourceCommit':identity.get('Config',{}).get('Labels',{}).get('org.opencontainers.image.revision'),'sha256':{s:hashlib.file_digest((out/(name+'-'+s)).open('rb'),'sha256').hexdigest() for s in ['scan.json','sbom.json','oci.tar']}}
 (out/(name+'-identity.private.json')).unlink()
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
PY
python3 "$repo/scripts/verify-release-evidence.py" "$output" | tee "$output/gate.json"
