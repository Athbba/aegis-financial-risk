#!/usr/bin/env bash
# Additional immutable rollback artifact. Main final-image gate must run first to refresh DBs.
set -euo pipefail
[[ $# == 3 && "$3" =~ ^[a-f0-9]{40}$ ]] || exit 2
out="$1"; image="$2"; expected="$3"; scanner="${TRIVY:-trivy}"
"$scanner" image --skip-db-update --scanners vuln --format json --output "$out/rollback-scan.json" "$image" > "$out/rollback-scanner.log" 2>&1
"$scanner" image --skip-db-update --format cyclonedx --output "$out/rollback-sbom.json" "$image" >> "$out/rollback-scanner.log" 2>&1
python3 scripts/oci-export.py "$image" "$out/rollback-oci.tar"
"$scanner" version --format json > "$out/rollback-scanner-version.json"
python3 - "$out" "$image" "$expected" <<'PY'
import collections,datetime,hashlib,json,pathlib,subprocess,sys
out=pathlib.Path(sys.argv[1]);image=sys.argv[2];expected=sys.argv[3]
identity=json.loads(subprocess.check_output(['docker','image','inspect',image],text=True))[0]
assert identity['Config']['Labels']['org.opencontainers.image.revision']==expected
scanpath=out/'rollback-scan.json';scan=json.loads(scanpath.read_text());assert scan['Metadata']['ImageID']==identity['Id'] and scan['Results']
config=scan.get('Metadata',{}).get('ImageConfig',{});config.pop('history',None)
for block in [config,config.get('config',{}),config.get('container_config',{})]:block.pop('Env',None);block.pop('env',None)
scanpath.write_text(json.dumps(scan,indent=2)+'\n')
version=json.loads((out/'rollback-scanner-version.json').read_text())
for name in ['VulnerabilityDB','JavaDB']:
 stamp=datetime.datetime.fromisoformat(version[name]['UpdatedAt'].replace('Z','+00:00'))
 assert 0<=(datetime.datetime.now(datetime.timezone.utc)-stamp).total_seconds()<=48*3600
counts=collections.Counter(v['Severity'] for r in scan['Results'] for v in r.get('Vulnerabilities',[]))
checksums={}
for suffix in ['scan.json','sbom.json','oci.tar']:
 with (out/('rollback-'+suffix)).open('rb') as stream:checksums[suffix]=hashlib.file_digest(stream,'sha256').hexdigest()
status='BLOCKED' if counts['HIGH'] or counts['CRITICAL'] else 'PASS'
report={'status':status,'imageId':identity['Id'],'sourceCommit':expected,'platform':identity['Os']+'/'+identity['Architecture'],'counts':dict(counts),'sha256':checksums,'scanner':version}
(out/'rollback.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report));assert status=='PASS'
PY
