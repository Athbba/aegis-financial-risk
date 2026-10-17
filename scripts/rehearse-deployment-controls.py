#!/usr/bin/env python3
"""Real proxy-chain client isolation, persisted budgets, private health and effective containers."""
import argparse,json,pathlib,subprocess,time
p=argparse.ArgumentParser();p.add_argument('--env-file',required=True);p.add_argument('--ca',required=True);p.add_argument('--output',required=True);a=p.parse_args()
repo=pathlib.Path(__file__).resolve().parents[1];compose=['docker','compose','--env-file',str(pathlib.Path(a.env_file).resolve()),'-f',str(repo/'compose.deployment.yml')]
resolved=json.loads(subprocess.check_output(compose+['config','--format','json'],text=True));project=resolved['name'];assert project.startswith('aegis-p5-')
def command(args,check=True):return subprocess.run(args,check=check,text=True,capture_output=True)
def health(kind):return command(compose+['exec','-T','backend','wget','-Y','off','-qO-','http://127.0.0.1:8080/actuator/health/'+kind],False)
def ready():
 for _ in range(60):
  if health('readiness').returncode==0:return
  time.sleep(.5)
 raise AssertionError('Readiness failed')
ready();state={}
for service in ['backend','frontend','db']:
 cid=command(compose+['ps','-q',service]).stdout.strip();d=json.loads(command(['docker','inspect',cid]).stdout)[0];host=d['HostConfig']
 uid=command(compose+['exec','-T',service,'id','-u']).stdout.strip();assert uid!='0'
 assert host['CapDrop']==['ALL'] and 'no-new-privileges:true' in host['SecurityOpt'] and host['Memory']>0 and host['PidsLimit']>0
 assert host['ReadonlyRootfs']==(service!='db')
 state[service]={'uid':int(uid),'capDrop':host['CapDrop'],'readOnlyRootfs':host['ReadonlyRootfs'],'memoryBytes':host['Memory'],'pidsLimit':host['PidsLimit'],'databaseWritableDataExpected':service=='db'}
clients=[];helper='python:3.12-alpine@sha256:1b668429b3511ab407d8e00648891631b0b1a4d7e15e3ca70f38ab5b91ad4ab4'
try:
 for name in ['client-a','client-b']:
  cid=command(['docker','run','-d','--rm','--name',project+'-'+name,'--network',project+'_edge','--cap-drop=ALL','--security-opt=no-new-privileges:true','--read-only','--memory=128m','--pids-limit=64','-v',str(pathlib.Path(a.ca).resolve())+':/tmp/aegis-ca:ro',helper,'sleep','600']).stdout.strip();clients.append(cid)
 def client(index,mode):
  return subprocess.check_output(['docker','exec','-i',clients[index],'python','-',mode],input=(repo/'scripts/proxy-client-fixture.py').read_text(),text=True).strip()
 burst=json.loads(client(0,'burst'));distinct=json.loads(client(1,'distinct'))
 command(compose+['restart','-t','15','backend']);ready();persisted=json.loads(client(0,'limited'))
 # Real database outage: readiness removes service, while liveness avoids a restart storm.
 command(compose+['stop','-t','5','db'])
 try:
  assert health('readiness').returncode!=0
  live=health('liveness');assert live.returncode==0 and 'UP' in live.stdout
 finally:command(compose+['start','db']);ready()
 report={'status':'PASS','scope':'LOCAL_COMPLETE_PROXY_CHAIN','containers':state,**burst,**distinct,**persisted,'databaseOutageReadinessDown':True,'databaseOutageLivenessUp':True,'expiry':'persistent-window controlled-clock PostgreSQL tests (no wall-clock shortening in deployment)'}
 pathlib.Path(a.output).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
finally:
 for cid in clients:command(['docker','stop','-t','1',cid],False)
