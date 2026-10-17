#!/usr/bin/env python3
"""Actual pg_dump/GPG/new-database restore, fingerprints and reconciliation on isolated Compose only."""
import argparse,datetime as dt,hashlib,json,os,pathlib,shlex,subprocess,tempfile,time,uuid
p=argparse.ArgumentParser();p.add_argument('--env-file',required=True);p.add_argument('--output',required=True);a=p.parse_args()
repo=pathlib.Path(__file__).resolve().parents[1];compose=['docker','compose','--env-file',str(pathlib.Path(a.env_file).resolve()),'-f',str(repo/'compose.deployment.yml')]
project=subprocess.check_output(compose+['config','--format','json'],text=True)
if not json.loads(project)['name'].startswith('aegis-p5-'):raise SystemExit('Only an isolated aegis-p5-* project is permitted')
def sql(query,db='aegis'):
 return subprocess.check_output(compose+['exec','-T','db','psql','-U','aegis_owner','-d',db,'-At','-c',query],text=True).strip()
tables=['app_user','portfolio','asset','historical_price','executed_order','position_lot','sell_allocation','cash_ledger','audit_event']
def fingerprint(db):
 return {table:sql("select md5(coalesce(string_agg(to_jsonb(t)::text,',' order by "+('symbol' if table=='asset' else 'id::text')+"),'')) from "+table+' t',db) for table in tables}
before=fingerprint('aegis');started=time.monotonic()
with tempfile.TemporaryDirectory(prefix='aegis-p5-recovery-') as tmp:
 root=pathlib.Path(tmp);keyring=root/'keyring';keyring.mkdir(mode=0o700);out=root/'encrypted';out.mkdir(mode=0o700);tools=root/'tools';tools.mkdir()
 # Standard PG client scripts run via the isolated DB container, no DB port publication.
 wrapper='''#!/usr/bin/env python3
import pathlib,subprocess,sys
compose=COMPOSE
name=pathlib.Path(sys.argv[0]).name;args=sys.argv[1:];files={}
if name=='pg_dump':
 for arg in args.copy():
  if arg.startswith('--file='): files['stdout']=open(arg[7:],'wb');args.remove(arg)
if name=='pg_restore':
 files['stdin']=open(args.pop(),'rb')
 if args and args[-1]=='--':args.pop()
raise SystemExit(subprocess.call(compose+['exec','-T','db',name,'-U','aegis_owner',*args],**files))
'''.replace('COMPOSE',repr(compose))
 for name in ['pg_dump','pg_restore','createdb']:
  f=tools/name;f.write_text(wrapper);f.chmod(0o700)
 # pg_dump default database is supplied explicitly via env in the container exec wrapper.
 f=tools/'pg_dump';f.write_text(f.read_text().replace("name,'-U','aegis_owner',*args", "name,'-U','aegis_owner','--dbname=aegis',*args"))
 quiet={'stdout':subprocess.DEVNULL,'stderr':subprocess.DEVNULL}
 subprocess.run(['gpg','--batch','--homedir',str(keyring),'--passphrase','','--quick-generate-key','AEGIS disposable recovery <restore@example.test>','rsa3072','encr','2d'],check=True,**quiet)
 keys=subprocess.check_output(['gpg','--homedir',str(keyring),'--with-colons','--list-keys'],text=True,stderr=subprocess.DEVNULL)
 recipient=next(l.split(':')[9] for l in keys.splitlines() if l.startswith('fpr:'))
 env={**os.environ,'PATH':str(tools)+os.pathsep+os.environ['PATH'],'BACKUP_GNUPGHOME':str(keyring),'PGDATABASE':'aegis','PGUSER':'aegis_owner'}
 result=subprocess.check_output([str(repo/'scripts/encrypted-backup.sh'),str(out),recipient],env=env,text=True,stderr=subprocess.DEVNULL)
 archive=pathlib.Path(result.strip().splitlines()[-1]);encrypted_bytes=archive.stat().st_size
 recovery_start=time.monotonic();plain=root/'restore.dump'
 subprocess.run(['gpg','--batch','--homedir',str(keyring),'--output',str(plain),'--decrypt',str(archive)],check=True,**quiet)
 pathlib.Path(str(plain)+'.sha256').write_text(hashlib.sha256(plain.read_bytes()).hexdigest()+'  restore.dump\n')
 target='aegis_restore_p5_'+uuid.uuid4().hex[:12]
 subprocess.run([str(repo/'scripts/restore-disposable-db.sh'),str(plain),target],env=env,check=True,**quiet)
 assert fingerprint(target)==before and fingerprint('aegis')==before
 reconciliation="""select
 not exists(select 1 from portfolio p where p.cash<>(select coalesce(sum(amount),0) from cash_ledger l where l.portfolio_id=p.id))
 and not exists(select 1 from position_lot l where l.remaining_quantity<>l.original_quantity-coalesce((select sum(quantity) from sell_allocation a where a.lot_id=l.id),0)
 or l.remaining_cost<>(select gross_amount from executed_order where id=l.buy_order_id)-coalesce((select sum(cost) from sell_allocation a where a.lot_id=l.id),0))
 and not exists(select 1 from executed_order o where not exists(select 1 from cash_ledger l where l.order_id=o.id and l.amount=case when o.side='BUY' then -o.gross_amount else o.gross_amount end)
 or not exists(select 1 from audit_event e where e.reference_id=o.id and e.event_type='SIMULATED_'||o.side))"""
 assert sql(reconciliation,target)=='t'
 measured=time.monotonic()-recovery_start
 # Integrity damage must fail before any restore; generated key and plaintext remain private scratch.
 bad=root/'damaged.gpg';data=bytearray(archive.read_bytes());data[-20]^=1;bad.write_bytes(data)
 assert subprocess.run(['gpg','--batch','--homedir',str(keyring),'--decrypt',str(bad)],**quiet).returncode!=0
 assert subprocess.run([str(repo/'scripts/restore-disposable-db.sh'),str(plain),target],env=env,**quiet).returncode!=0
 assert fingerprint('aegis')==before
 report={'status':'PASS','scope':'LOCAL_ENCRYPTED_ROUND_TRIP','sourceUnchanged':True,'financialTableFingerprints':len(before),'restoredReconciliation':'PASS (cash, FIFO lots, ledger/audit references SQL)','refusesExistingDatabase':True,'ciphertextIntegrityFailureDetected':True,'encryptedBytes':encrypted_bytes,'rpoTargetHours':24,'localBackupAgeSeconds':round(time.monotonic()-started,3),'rtoTargetSeconds':1800,'measuredRestoreSeconds':round(measured,3),'totalDrillSeconds':round(time.monotonic()-started,3),'remoteStorageVerified':False,'targetHostVerified':False}
 assert measured<1800
 pathlib.Path(a.output).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
