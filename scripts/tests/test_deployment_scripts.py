import pathlib,subprocess,tempfile,os,unittest
ROOT=pathlib.Path(__file__).parents[2]
class DeploymentScriptsTest(unittest.TestCase):
 def test_scanner_network_failure_stops_pipeline(self):
  with tempfile.TemporaryDirectory() as t:
   p=pathlib.Path(t)/'scanner';p.write_text('#!/bin/sh\nexit 42\n');p.chmod(0o700)
   result=subprocess.run([str(ROOT/'scripts/scan-release-images.sh'),t+'/out','api','ui','db'],env={**os.environ,'TRIVY':str(p)},capture_output=True)
   self.assertNotEqual(result.returncode,0);self.assertFalse((pathlib.Path(t)/'out/manifest.json').exists())
 def test_unsafe_config_and_unauthorized_upload_fail(self):
  result=subprocess.run(['python3',str(ROOT/'scripts/validate-deployment.py')],input=b'{"services":{}}',capture_output=True);self.assertNotEqual(result.returncode,0)
  result=subprocess.run([str(ROOT/'scripts/upload-backup.sh'),'unused.dump.gpg'],env={**os.environ,'AEGIS_REMOTE_BACKUP_AUTHORIZED':''},capture_output=True);self.assertNotEqual(result.returncode,0)
if __name__=='__main__':unittest.main()
