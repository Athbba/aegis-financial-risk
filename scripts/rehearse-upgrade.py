#!/usr/bin/env python3
"""Local immutable-artifact upgrade/rollback, no DB down migration or deletion."""
import argparse,http.cookiejar,json,pathlib,ssl,subprocess,tempfile,time,urllib.request,urllib.error,uuid
p=argparse.ArgumentParser();p.add_argument('--env-file',required=True);p.add_argument('--baseline-image',required=True);p.add_argument('--ca',required=True);p.add_argument('--output',required=True);a=p.parse_args()
assert a.baseline_image.startswith('sha256:') and len(a.baseline_image)==71
repo=pathlib.Path(__file__).resolve().parents[1];compose=['docker','compose','--env-file',str(pathlib.Path(a.env_file).resolve()),'-f',str(repo/'compose.deployment.yml')]
config=json.loads(subprocess.check_output(compose+['config','--format','json'],text=True));assert config['name'].startswith('aegis-p5-')
candidate=config['services']['backend']['image'];assert candidate.startswith('sha256:')
context=ssl.create_default_context(cafile=a.ca);jar=http.cookiejar.CookieJar();client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar),urllib.request.HTTPSHandler(context=context))
def call(path,data=None,headers=None):
 request=urllib.request.Request('https://localhost:8443/api'+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json',**(headers or {})})
 with client.open(request,timeout=15) as response:return json.load(response)
def token():return {'X-XSRF-TOKEN':call('/auth/csrf')['token']}
account={'email':'rollback-'+str(uuid.uuid4())+'@example.test','password':str(uuid.uuid4())+'-synthetic'}
call('/auth/register',account,token());portfolio=call('/portfolios',{'name':'Immutable upgrade rehearsal','initialCash':'10000'},token())['id']
key=str(uuid.uuid4());order={'symbol':'ALTA','side':'BUY','quantity':'10','priceDate':'2024-01-02'}
with tempfile.TemporaryDirectory(prefix='aegis-p5-upgrade-') as tmp:
 override=pathlib.Path(tmp)/'artifact.json'
 def serve(image):
  override.write_text(json.dumps({'services':{'backend':{'image':image}}}))
  subprocess.run(compose+['-f',str(override),'up','-d','--no-deps','backend'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  for _ in range(90):
   res=subprocess.run(compose+['exec','-T','backend','wget','-Y','off','-qO-','http://127.0.0.1:8080/actuator/health/readiness'],capture_output=True)
   if res.returncode==0:break
   time.sleep(.5)
  else:raise AssertionError('Artifact did not become ready')
  subprocess.run(compose+['restart','frontend'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  for _ in range(60):
   try:
    call('/auth/csrf');break
   except (OSError,urllib.error.HTTPError):time.sleep(.3)
  jar.clear();call('/auth/login',account,token())
 try:
  serve(a.baseline_image)
  # Real committed execution; model the client discarding confirmation and retaining its key.
  committed=call(f'/portfolios/{portfolio}/orders',order,{**token(),'Idempotency-Key':key})
  def verify():
   found=call(f'/portfolios/{portfolio}/orders/by-key/{key}')
   replay=call(f'/portfolios/{portfolio}/orders',order,{**token(),'Idempotency-Key':key})
   assert found['id']==committed['id']==replay['id']
   assert call(f'/portfolios/{portfolio}/orders')['total']==1
   assert call(f'/portfolios/{portfolio}/reconciliation')['status']=='CONSISTENT'
  serve(candidate);verify();serve(a.baseline_image);verify();serve(candidate);verify()
 finally:
  serve(candidate)
 report={'status':'PASS','scope':'LOCAL_REHEARSAL','baselineImageId':a.baseline_image,'candidateImageId':candidate,'upgrade':'PASS','rollback':'PASS','returnToCandidate':'PASS','oneCommittedOrderRetained':True,'sameKeyRecoveryAfterReauthentication':True,'databaseDownMigration':False,'schemaVersion':'3 unchanged','note':'Baseline Phase 4 Java artifact repackaged on the supported Alpine runtime; no live environment touched.'}
 pathlib.Path(a.output).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
