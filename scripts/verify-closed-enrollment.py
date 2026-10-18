#!/usr/bin/env python3
import http.cookiejar,json,ssl,sys,urllib.request,urllib.error,uuid
context=ssl.create_default_context(cafile=sys.argv[1]);jar=http.cookiejar.CookieJar();client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar),urllib.request.HTTPSHandler(context=context))
with client.open('https://localhost:8443/api/auth/csrf') as r:token=json.load(r)['token']
request=urllib.request.Request('https://localhost:8443/api/auth/register',data=json.dumps({'email':str(uuid.uuid4())+'@example.test','password':'closed-enrollment-test-password'}).encode(),headers={'Content-Type':'application/json','X-XSRF-TOKEN':token})
try:client.open(request);raise AssertionError('Closed enrollment admitted registration')
except urllib.error.HTTPError as e:assert e.code==403
print('{"closedEnrollment":"PASS","registrationStatus":403}')
