import runpy,time,datetime,concurrent.futures,urllib.request,http.cookiejar
g=runpy.run_path(str(__import__('pathlib').Path(__file__).with_name('continuity-mysql.py')));req=g['req'];state=req.__globals__;student=g['student'];g['restoreAdmin']()
data,kind=state['multipart']({'name':'限兑与消息测试','requiredPoints':1,'stock':3,'enabled':'true'});reward=req('/api/rankings/admin/rewards','POST',data,kind)
rule={'lowStockThreshold':2,'perStudentLimit':1,'startsAt':(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=1)).isoformat(),'endsAt':None,'instructions':'领取时联系老师'}
path='/api/rankings/admin/reward-rules/'+str(reward['id']);req(path,'PUT',rule)
def redeem():return req('/api/rankings/admin/redemptions','POST',{'studentId':student,'rewardId':reward['id']},expected=(201,409))
blocked=redeem();assert '尚未开始' in blocked['error'];rule['startsAt']=None;rule['endsAt']=(datetime.datetime.now(datetime.timezone.utc)-datetime.timedelta(seconds=5)).isoformat();req(path,'PUT',rule);assert '已结束' in redeem()['error']
rule['endsAt']=None;req(path,'PUT',rule)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda _:redeem(),range(2)))
orders=[r for r in results if 'id' in r];assert len(orders)==1,results;order=orders[0]
assert any(r['id']==reward['id'] and r['stock']==2 for r in req('/api/rankings/admin/rewards'))
alerts=req('/api/rankings/admin/alerts');assert any(r['id']==reward['id'] for r in alerts['lowStock'])
messages=req('/api/rankings/admin/messages');assert messages['unread']>0
mid=messages['items'][0]['id'];req('/api/rankings/admin/messages/'+str(mid)+'/read','POST',{});req('/api/rankings/admin/messages/'+str(mid)+'/read','POST',{})
# Student session only: same phone is ambiguous, explicit ID selects the stable transferred account.
studentClient=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));state['client']=studentClient;state['csrf']='';studentCsrf=req('/api/auth/csrf')['token'];state['csrf']=studentCsrf
req('/api/public/rankings/student-auth/login','POST',{'phone':g['g']['phone'],'password':'student-new-test-123','studentId':student})
box=req('/api/public/rankings/student-messages');assert box['unread']>0;sid=box['items'][0]['id'];req('/api/public/rankings/student-messages/'+str(mid)+'/read','POST',{},expected=(409,));req('/api/public/rankings/student-messages/'+str(sid)+'/read','POST',{})
req(f'/api/public/rankings/student-redemptions/{order["id"]}/cancel-request','POST',{'reason':'释放限兑次数'})
g['restoreAdmin']();req('/api/rankings/admin/aftercare/cancellations/'+str(order['id'])+'/decision','POST',{'approved':True,'reason':'确认退款释放次数'})
newOrder=redeem();assert 'id' in newOrder;req('/api/rankings/admin/redemptions/'+str(newOrder['id'])+'/fulfillment','PATCH',{'fulfilled':True})
# Cross-owner reads are empty and writes cannot target another teacher's prize or lesson.
name='privacy'+str(time.time_ns())[-12:];password='isolated-teacher-test-123';teacher=req('/api/admin/users','POST',{'username':name,'password':password,'confirmation':password});req('/api/admin/users/'+str(teacher['id'])+'/permissions','PUT',{'permissions':['rankings.view','rankings.reward_rules.read','rankings.reward_rules.edit','rankings.messages.read','rankings.messages.update','rankings.alerts.read']});g['loginTeacher'](name,password)
assert not req('/api/rankings/admin/reward-rules');assert req('/api/rankings/admin/messages')['total']==0
req(path,'PUT',rule,expected=(409,));req('/api/rankings/admin/messages/'+str(mid)+'/read','POST',{},expected=(409,))
g['restoreAdmin']();assert req('/api/rankings/admin/operations/reconciliations','POST',{})['anomalies']==0
print('PASS: MySQL reward time windows, concurrent cumulative limit, cancellation releases limit, low-stock alerts, student/teacher message isolation, read idempotence, cross-tenant denial and zero ledger mismatches')
