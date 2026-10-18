#!/usr/bin/env python3
"""Fail closed: immutable final-image identity, complete scans/SBOM and DB age <=48h."""
import argparse, collections, datetime as dt, hashlib, json, pathlib, sys, re

def verify(root):
    manifest=json.loads((root/'manifest.json').read_text())
    now=dt.datetime.now(dt.timezone.utc)
    def fresh(value):
        stamp=dt.datetime.fromisoformat(value.replace('Z','+00:00'))
        age=(now-stamp).total_seconds()
        if not 0<=age<=48*3600: raise ValueError('missing, future or stale vulnerability DB timestamp')
    if manifest.get('scanner')!='Trivy' or not manifest.get('scannerVersion') or not re.fullmatch('[a-f0-9]{40}',manifest.get('sourceCommit','')):
        raise ValueError('missing scanner/source identity')
    fresh(manifest['database']['UpdatedAt'])
    fresh(manifest['javaDatabase']['UpdatedAt'])
    if set(manifest['images'])!={'api','ui','database'}: raise ValueError('missing final image')
    summary={}
    for name, evidence in manifest['images'].items():
        if evidence['sourceCommit']!=manifest['sourceCommit']: raise ValueError('image/source commit mismatch')
        scan=json.loads((root/(name+'-scan.json')).read_text())
        sbom=json.loads((root/(name+'-sbom.json')).read_text())
        if not re.fullmatch('sha256:[a-f0-9]{64}',evidence['imageId']) or evidence['platform'] not in ['linux/amd64','linux/arm64']:
            raise ValueError('missing immutable platform/image ID')
        if scan.get('Metadata',{}).get('ImageID')!=evidence['imageId'] or not scan.get('Results'):
            raise ValueError('scan identity mismatch/incomplete results')
        if scan.get('SchemaVersion')!=2 or not scan.get('Metadata',{}).get('OS'):
            raise ValueError('invalid scanner output')
        if sbom.get('bomFormat')!='CycloneDX' or not sbom.get('components'): raise ValueError('missing SBOM components')
        for suffix in ['scan.json','sbom.json','oci.tar']:
            path=root/(name+'-'+suffix)
            with path.open('rb') as stream: digest=hashlib.file_digest(stream,'sha256').hexdigest()
            if evidence['sha256'][suffix]!=digest: raise ValueError('artifact checksum mismatch')
        counts=collections.Counter(v['Severity'] for r in scan['Results'] for v in r.get('Vulnerabilities',[]))
        summary[name]=dict(counts)
        if counts['CRITICAL'] or counts['HIGH']: raise ValueError('Critical/High finding: '+name)
    return summary

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('directory',type=pathlib.Path);a=p.parse_args()
    try: print(json.dumps({'status':'PASS','counts':verify(a.directory)},indent=2))
    except (OSError,ValueError,KeyError,TypeError) as e:
        print(json.dumps({'status':'BLOCKED','reason':str(e)}));sys.exit(1)
