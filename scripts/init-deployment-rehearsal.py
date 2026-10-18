#!/usr/bin/env python3
"""Generate private disposable TLS/secrets outside the checkout; never changes existing files."""
import os,pathlib,secrets,subprocess,sys,json,ipaddress,re
root=pathlib.Path(sys.argv[1]).resolve()
if root.exists():sys.exit('Refusing existing rehearsal directory')
project=os.environ.get('AEGIS_REHEARSAL_PROJECT','aegis-p5-rehearsal')
if not re.fullmatch(r'aegis-p5-[a-z0-9-]+',project):sys.exit('Use isolated aegis-p5-* project')
ids=subprocess.check_output(['docker','network','ls','-q'],text=True).split()
networks=json.loads(subprocess.check_output(['docker','network','inspect',*ids],text=True)) if ids else []
used=[ipaddress.ip_network(c['Subnet']) for n in networks for c in (n.get('IPAM',{}).get('Config') or []) if c.get('Subnet')]
subnet=next(ipaddress.ip_network(f'172.29.{i}.0/24') for i in range(51,201) if not any(ipaddress.ip_network(f'172.29.{i}.0/24').overlaps(n) for n in used if n.version==4))
proxy=str(subnet.network_address+10)
root.mkdir(mode=0o700)
def run(*args):subprocess.run(args,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
for name in ['owner-password','runtime-password']:
 (root/name).write_text(secrets.token_urlsafe(36))
(root/'operations-hash').write_text('')
run('openssl','req','-x509','-newkey','rsa:3072','-nodes','-keyout',str(root/'ca-key'),'-out',str(root/'ca-cert'),'-days','3','-subj','/CN=AEGIS disposable rehearsal CA')
run('openssl','req','-newkey','rsa:3072','-nodes','-keyout',str(root/'tls-key'),'-out',str(root/'request.csr'),'-subj','/CN=localhost')
(root/'extensions').write_text('subjectAltName=DNS:localhost,DNS:frontend,IP:127.0.0.1\nextendedKeyUsage=serverAuth\nbasicConstraints=CA:FALSE\n')
run('openssl','x509','-req','-in',str(root/'request.csr'),'-CA',str(root/'ca-cert'),'-CAkey',str(root/'ca-key'),'-CAcreateserial','-CAserial',str(root/'ca-serial'),'-out',str(root/'tls-cert'),'-days','2','-extfile',str(root/'extensions'))
for p in root.iterdir():p.chmod(0o600)
# Compose file secrets are bind mounts: non-root uid needs read permission. The parent stays 0700.
for name in ['runtime-password','operations-hash','tls-key','tls-cert','owner-password','ca-cert']:(root/name).chmod(0o444)
(root/'deployment.env').write_text(f'DEPLOY_PROJECT={project}\nDEPLOY_SUBNET={subnet}\nDEPLOY_PROXY_IP={proxy}\nDEPLOY_SECRETS={root}\nAPI_IMAGE=aegis-p5-api:rehearsal\nUI_IMAGE=aegis-p5-ui:rehearsal\nDATABASE_IMAGE=aegis-p5-db:rehearsal\nDEMO_ENROLLMENT=open\n')
(root/'deployment.env').chmod(0o600)
print('Created disposable secrets and locally signed TLS certificate. CA key stays private; never publish this directory.')
