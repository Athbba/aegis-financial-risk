#!/usr/bin/env python3
"""Run INSIDE disposable clients on the edge network; print statuses, never credentials/cookies."""
import http.cookiejar,json,ssl,sys,urllib.request,urllib.error,uuid
mode=sys.argv[1];jar=http.cookiejar.CookieJar()
client=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPCookieProcessor(jar),urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile='/tmp/aegis-ca')))
def request(path,data=None,headers=None):
 req=urllib.request.Request('https://frontend:8443/api'+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json',**(headers or {})})
 try:
  with client.open(req,timeout=15) as res:return res.status,json.load(res)
 except urllib.error.HTTPError as e:return e.code,{}
status,csrf=request('/auth/csrf');assert status==200
headers={'X-XSRF-TOKEN':csrf['token']}
if mode=='burst':
 codes=[]
 for i in range(32):
  codes.append(request('/auth/login',{'email':str(uuid.uuid4())+'@example.test','password':'proxy-negative-test-password'}, {**headers,'X-Forwarded-For':f'198.51.100.{i+1}','X-Aegis-Client-IP':f'203.0.113.{i+1}','Forwarded':'for=192.0.2.99'})[0])
 assert codes[:30]==[401]*30 and codes[30:]==[429,429],codes
 print(json.dumps({'abusiveClientRequests':32,'unauthorized':30,'throttled':2,'spoofingDidNotEvade':True}))
elif mode=='limited':
 assert request('/auth/login',{'email':str(uuid.uuid4())+'@example.test','password':'proxy-negative-test-password'},headers)[0]==429
 print('{"budgetSurvivedRestart":true}')
elif mode=='distinct':
 account={'email':str(uuid.uuid4())+'@example.test','password':'proxy-isolation-fixture-password'}
 assert request('/auth/register',account,headers)[0]==201
 _,csrf=request('/auth/csrf');headers={'X-XSRF-TOKEN':csrf['token']}
 assert request('/auth/login',account,headers)[0]==200
 print('{"distinctClientCanAuthenticate":true}')
else:raise SystemExit('Unknown mode')
