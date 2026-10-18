import importlib.util, unittest, tempfile, pathlib, json, datetime as dt, hashlib
spec=importlib.util.spec_from_file_location('gate',pathlib.Path(__file__).parents[1]/'verify-release-evidence.py');gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
class GateTest(unittest.TestCase):
 def fixture(self,root):
  manifest={'scanner':'Trivy','scannerVersion':'0.76.0','sourceCommit':'a'*40,'database':{'UpdatedAt':dt.datetime.now(dt.timezone.utc).isoformat()},'javaDatabase':{'UpdatedAt':dt.datetime.now(dt.timezone.utc).isoformat()},'images':{}}
  for name in ['api','ui','database']:
   scan={'SchemaVersion':2,'Metadata':{'ImageID':'sha256:'+'a'*64,'OS':{'Family':'alpine'}},'Results':[{'Target':'fixture','Vulnerabilities':[]}]}
   (root/(name+'-scan.json')).write_text(json.dumps(scan));(root/(name+'-sbom.json')).write_text(json.dumps({'bomFormat':'CycloneDX','components':[{'name':'test'}]}));(root/(name+'-oci.tar')).write_bytes(b'fixture')
   manifest['images'][name]={'imageId':'sha256:'+'a'*64,'platform':'linux/amd64','sourceCommit':'a'*40,'sha256':{s:hashlib.sha256((root/(name+'-'+s)).read_bytes()).hexdigest() for s in ['scan.json','sbom.json','oci.tar']}}
  (root/'manifest.json').write_text(json.dumps(manifest));return manifest
 def test_clean_and_vulnerable_fixture(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=pathlib.Path(tmp);self.fixture(root);self.assertEqual(set(gate.verify(root)),{'api','ui','database'})
   p=root/'api-scan.json';d=json.loads(p.read_text());d['Results'][0]['Vulnerabilities']=[{'Severity':'CRITICAL'}];p.write_text(json.dumps(d))
   m=json.loads((root/'manifest.json').read_text());m['images']['api']['sha256']['scan.json']=hashlib.sha256(p.read_bytes()).hexdigest();(root/'manifest.json').write_text(json.dumps(m))
   with self.assertRaisesRegex(ValueError,'Critical/High'):gate.verify(root)
 def test_stale_missing_scanner_and_tampering_fail(self):
  for failure in ['stale','missing','scanner','tamper']:
   with self.subTest(failure=failure),tempfile.TemporaryDirectory() as tmp:
    root=pathlib.Path(tmp);m=self.fixture(root)
    if failure=='stale':m['database']['UpdatedAt']=(dt.datetime.now(dt.timezone.utc)-dt.timedelta(hours=49)).isoformat()
    if failure=='missing':(root/'ui-scan.json').unlink()
    if failure=='scanner':m['scannerVersion']=''
    if failure=='tamper':(root/'ui-sbom.json').write_text('{}')
    (root/'manifest.json').write_text(json.dumps(m))
    with self.assertRaises((ValueError,OSError)):gate.verify(root)
if __name__=='__main__':unittest.main()
