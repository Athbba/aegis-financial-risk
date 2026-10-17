#!/usr/bin/env python3
"""Trusted HTTPS + bounded concurrent read smoke; no logs containing session/account values."""
import argparse,concurrent.futures,http.cookiejar,json,math,ssl,time,urllib.request,urllib.error,uuid
p=argparse.ArgumentParser();p.add_argument('--ca',required=True);p.add_argument('--url',default='https://localhost:8443');p.add_argument('--output',required=True);a=p.parse_args()
context=ssl.create_default_context(cafile=a.ca);jar=http.cookiejar.CookieJar();client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar),urllib.request.HTTPSHandler(context=context))
def call(path,data=None,headers=None):
 req=urllib.request.Request(a.url+'/api'+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json',**(headers or {})})
 try:
  with client.open(req,timeout=15) as r:return r.status,json.load(r),r.headers
 except urllib.error.HTTPError as e:return e.code,{},e.headers
_,token,_=call('/auth/csrf');headers={'X-XSRF-TOKEN':token['token']}
account={'email':'smoke-'+str(uuid.uuid4())+'@example.test','password':str(uuid.uuid4())+'demo'}
assert call('/auth/register',account,headers)[0]==201
_,token,_=call('/auth/csrf');headers={'X-XSRF-TOKEN':token['token']}
assert all(c.secure for c in jar if c.name in ['JSESSIONID','XSRF-TOKEN'])
status,portfolio,_=call('/portfolios',{'name':'Phase 5 isolated smoke','initialCash':'10000'},headers);assert status==201
pid=portfolio['id']
for side,quantity in [('BUY','10'),('SELL','4')]:
 status,_,_=call(f'/portfolios/{pid}/orders',{'symbol':'ALTA','side':side,'quantity':quantity,'priceDate':'2024-01-02'},{**headers,'Idempotency-Key':str(uuid.uuid4())});assert status==200
assert call(f'/portfolios/{pid}/reconciliation')[1]['status']=='CONSISTENT'
assert call(f'/portfolios/{pid}/orders',{}, {})[0]==403
assert call('/portfolios/'+str(uuid.uuid4())+'/reconciliation')[0]==404
try:
 urllib.request.urlopen(a.url+'/actuator/metrics',context=context)
 raise AssertionError('Metrics exposed at edge')
except urllib.error.HTTPError as e: assert e.code==404
# Copy only cookies in memory into each worker; no artifact contains them.
cookie='; '.join(c.name+'='+c.value for c in jar)
def read(_):
 start=time.monotonic()
 req=urllib.request.Request(a.url+f'/api/portfolios/{pid}/risk/summary?date=2024-01-02&lookbackDays=730',headers={'Cookie':cookie})
 try:
  with urllib.request.urlopen(req,context=context,timeout=15) as r:status=r.status;r.read()
 except urllib.error.HTTPError as e:status=e.code
 return (time.monotonic()-start)*1000,status
def worker(_): return [read(i) for i in range(20)]
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=[item for group in pool.map(worker,range(4)) for item in group]
latencies=sorted(t for t,_ in results);errors=sum(s!=200 for _,s in results)
report={'scope':'LOCAL_REHEARSAL','clients':4,'requests':80,'errors':errors,'errorRatio':errors/80,'p95Ms':latencies[math.ceil(.95*80)-1],'thresholds':{'errorRatioBelow':.01,'p95MsBelow':1000},'tradingAndReconciliation':'PASS','secureCookies':'PASS'}
report['status']='PASS' if errors/80<.01 and report['p95Ms']<1000 else 'FAIL'
open(a.output,'w').write(json.dumps(report,indent=2)+'\n');print(json.dumps(report));assert report['status']=='PASS'
