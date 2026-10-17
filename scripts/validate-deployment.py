#!/usr/bin/env python3
"""Validate resolved deployment Compose JSON without printing secret values."""
import json,sys
try:
 d=json.load(sys.stdin);services=d['services'];api=services['backend'];env=api['environment']
 assert env['SPRING_PROFILES_ACTIVE']=='deployment'
 assert env['COOKIE_SECURE']=='true' and env['SPRING_FLYWAY_ENABLED']=='false' and env['AEGIS_SEED_DATA']=='false'
 assert env['AEGIS_ENROLLMENT'] in ['closed','open'] and env['AEGIS_TRUSTED_PROXY']==services['frontend']['networks']['private']['ipv4_address']
 assert not any('MIGRATION' in k or k in ['POSTGRES_PASSWORD','DATABASE_PASSWORD'] for k in env)
 assert {s['source'] for s in api['secrets']}=={'runtime-password','operations-hash'}
 for name in ['backend','frontend','db']:
  s=services[name];assert s['image'].startswith('sha256:') and len(s['image'])==71
  assert 'ALL' in s['cap_drop'] and 'no-new-privileges:true' in s['security_opt']
  assert s.get('pids_limit',0)>0 and int(s.get('mem_limit',0))>0
 for name in ['backend','migrate','provision']:
  assert services[name]['read_only'] and 'ALL' in services[name]['cap_drop']
 assert services['frontend']['read_only'] and services['db']['user']=='postgres'
 assert not api.get('ports') and not services['db'].get('ports')
 assert services['db'].get('volumes') and not services['db'].get('read_only',False)
 assert all(p['host_ip']=='127.0.0.1' for p in services['frontend']['ports'])
 print('{"deploymentConfiguration":"PASS"}')
except (AssertionError,ValueError,KeyError,TypeError):
 print('{"deploymentConfiguration":"BLOCKED","reason":"Unsafe or incomplete effective settings"}');sys.exit(1)
