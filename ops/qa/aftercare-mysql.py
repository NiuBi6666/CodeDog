import runpy,urllib.request,urllib.error,http.cookiejar,json,time,concurrent.futures
g=runpy.run_path(str(__import__('pathlib').Path(__file__).with_name('points-operations-mysql.py')));req=g['req'];reqglobals=req.__globals__;student=g['student'];teacher=reqglobals['client'];teacherCsrf=reqglobals['csrf'];prefix='/api/rankings/admin/aftercare'
phone='199'+str(time.time_ns())[-8:]
req('/api/rankings/admin/students/'+student+'/phone','PUT',{'phone':phone,'expectedPhone':''})
data,kind=g['multipart']({'name':'退款事务测试','requiredPoints':1,'stock':2,'enabled':'true'})
reward=req('/api/rankings/admin/rewards','POST',data,kind)
studentClient=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def switch(who):
 reqglobals['client']=teacher if who=='teacher' else studentClient
 reqglobals['csrf']=teacherCsrf if who=='teacher' else studentCsrf
reqglobals['client']=studentClient;reqglobals['csrf']='';studentCsrf=req('/api/auth/csrf')['token'];reqglobals['csrf']=studentCsrf
profile=req('/api/public/rankings/student-auth/login','POST',{'phone':phone,'password':'123456'});assert profile['mustChangePassword']
before=req('/api/public/rankings/student-balance')['availablePoints']
key='qa-idempotency-'+str(time.time_ns())
def redeem():
 request=urllib.request.Request(g['base']+f'/api/public/rankings/rewards/{reward["id"]}/redeem',data=b'{}',headers={'Content-Type':'application/json','X-XSRF-TOKEN':studentCsrf,'Idempotency-Key':key},method='POST')
 with studentClient.open(request,timeout=30)as r:return json.load(r)
order=redeem();again=redeem();assert order['id']==again['id'];assert req('/api/public/rankings/student-balance')['availablePoints']==before-1
req(f'/api/public/rankings/student-redemptions/{order["id"]}/cancel-request','POST',{'reason':'误兑换'})
switch('teacher');req(f'/api/rankings/admin/redemptions/{order["id"]}/fulfillment','PATCH',{'fulfilled':True},expected=(404,))
req(f'/api/rankings/admin/rewards/{reward["id"]}','DELETE',expected=(204,))
def approve():return req(prefix+f'/cancellations/{order["id"]}/decision','POST',{'approved':True,'reason':'核实后退款'},expected=(200,409))
with concurrent.futures.ThreadPoolExecutor(max_workers=2)as pool:answers=list(pool.map(lambda _:approve(),range(2)))
assert sum(1 for a in answers if a.get('refunded'))==1,answers
assert not any(r['id']==reward['id'] for r in req('/api/rankings/admin/rewards'))
switch('student');assert req('/api/public/rankings/student-balance')['availablePoints']==before
req(f'/api/public/rankings/student-redemptions/{order["id"]}/cancel-request','POST',{'reason':'重复'},expected=(409,))
valid=req('/api/public/rankings/student-auth/recovery','POST',{'phone':phone,'studentId':student,'reason':'忘记密码'})
invalid=req('/api/public/rankings/student-auth/recovery','POST',{'phone':'10000000000','studentId':'unknown','reason':'忘记密码'});assert valid==invalid
switch('teacher');application=next(x for x in req(prefix+'/recoveries') if x['student_id']==student)
req(prefix+f'/recoveries/{application["id"]}/verify','POST',{'approved':True,'reason':'线下核实学员身份'})
req(prefix+f'/recoveries/{application["id"]}/resolve','POST',{'password':'temporary-test-123'})
switch('student');req('/api/public/rankings/student-auth/session',expected=(401,))
req('/api/public/rankings/student-auth/login','POST',{'phone':phone,'password':'123456'},expected=(401,))
profile=req('/api/public/rankings/student-auth/login','POST',{'phone':phone,'password':'temporary-test-123'});assert profile['mustChangePassword']
csrfUpdated=req('/api/auth/csrf')['token'];studentCsrf=csrfUpdated;reqglobals['csrf']=studentCsrf
profile=req('/api/public/rankings/student-auth/change-password','POST',{'password':'student-new-test-123'});assert not profile['mustChangePassword']
switch('teacher');report=req('/api/rankings/admin/operations/reconciliations','POST',{});assert report['anomalies']==0,report
print('PASS: MySQL idempotent redemption, cancellation approval race refunds once, deleted prize stays offline, recovery response does not reveal existence, teacher verification, administrator reset, old session revoked, forced password reminder, ledger reconciles')
