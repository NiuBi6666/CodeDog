import runpy,subprocess,re,json,concurrent.futures,urllib.request,urllib.error,http.cookiejar
g=runpy.run_path(str(__import__('pathlib').Path(__file__).with_name('engagement-mysql.py')));req=g['req'];state=req.__globals__;phase3=g['g'];phase3['restoreAdmin']();student=g['student'];owner=state['env'].get('ADMIN_USERNAME','admin');target=g['name'];secondary=phase3['second']
def sql(statement):
 result=subprocess.run(['docker','exec','codedog-ops-mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -N -B -e "$1"','sh',statement],text=True,capture_output=True,check=True);return result.stdout.strip()
assert all(re.fullmatch('[A-Za-z0-9_-]+',s)for s in (student,secondary,owner,target))
prefix='/api/rankings/admin/transfers';before=state['own']()['availablePoints'];preview=req(prefix+'/preview','POST',{'from':owner,'to':target,'studentIds':[student,secondary],'offboard':False,'reason':'多学员原子回滚验证'})
sql(f"INSERT INTO ranking_student_accounts(phone,owner_username,student_id,student_name,password_hash,enabled) SELECT phone,'{target}',student_id,student_name,password_hash,TRUE FROM ranking_student_accounts WHERE owner_username='{owner}' AND student_id='{secondary}'")
req(prefix+'/'+preview['previewId']+'/confirm','POST',{},expected=(409,))
assert state['own']()['availablePoints']==before
assert sql(f"SELECT owner_username FROM ranking_current_ownership WHERE student_id='{student}'")==owner
assert sql(f"SELECT owner_username FROM ranking_student_accounts WHERE student_id='{student}'")==owner
assert sql(f"SELECT COUNT(*) FROM ranking_transfers WHERE student_id='{student}' AND to_owner='{target}'")=='0'
sql(f"DELETE FROM ranking_student_accounts WHERE owner_username='{target}' AND student_id='{secondary}'")
stale=req(prefix+'/preview','POST',{'from':owner,'to':target,'studentIds':[student],'offboard':False,'reason':'陈旧预览校验'});state['adjust'](before+1);req(prefix+'/'+stale['previewId']+'/confirm','POST',{},expected=(409,));state['adjust'](before)
# Upper balance limit: refund overflow must roll back order, stock and ledger as one transaction.
state['adjust'](999999999);data,kind=state['multipart']({'name':'余额边界验证','requiredPoints':1,'stock':2,'enabled':'true'});reward=req('/api/rankings/admin/rewards','POST',data,kind);order=req('/api/rankings/admin/redemptions','POST',{'studentId':student,'rewardId':reward['id']});state['adjust'](999999999)
phone=sql(f"SELECT phone FROM ranking_student_accounts WHERE owner_username='{owner}' AND student_id='{student}'")
client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));state['client']=client;state['csrf']='';token=req('/api/auth/csrf')['token'];state['csrf']=token;req('/api/public/rankings/student-auth/login','POST',{'phone':phone,'password':'student-new-test-123','studentId':student})
req(f'/api/public/rankings/student-redemptions/{order["id"]}/cancel-request','POST',{'reason':'余额上限测试'})
req('/api/public/rankings/student-opportunities?studentId='+secondary,expected=(403,))
phase3['restoreAdmin']();decision='/api/rankings/admin/aftercare/cancellations/'+str(order['id'])+'/decision';req(decision,'POST',{'approved':True,'reason':'上限应阻止退款'},expected=(409,));assert state['own']()['availablePoints']==999999999
assert sql(f"SELECT status FROM ranking_reward_redemptions WHERE id={order['id']}")=='CANCEL_REQUESTED';assert sql(f"SELECT stock FROM ranking_rewards WHERE id={reward['id']}")=='1'
state['adjust'](999999998);req(decision,'POST',{'approved':True,'reason':'余额恢复允许退款'});assert state['own']()['availablePoints']==999999999;state['adjust'](before)
# Fulfillment and cancellation race; only one state transition may succeed.
order=req('/api/rankings/admin/redemptions','POST',{'studentId':student,'rewardId':reward['id']})
def send(opener,csrf,path,method,body):
 request=urllib.request.Request(state['base']+path,data=json.dumps(body).encode(),headers={'Content-Type':'application/json','X-XSRF-TOKEN':csrf},method=method)
 try:
  with opener.open(request,timeout=30)as response:return response.status
 except urllib.error.HTTPError as e:return e.code
with concurrent.futures.ThreadPoolExecutor(max_workers=2)as pool:
 a=pool.submit(send,phase3['admin'],phase3['adminCsrf'],f'/api/rankings/admin/redemptions/{order["id"]}/fulfillment','PATCH',{'fulfilled':True})
 b=pool.submit(send,client,token,f'/api/public/rankings/student-redemptions/{order["id"]}/cancel-request','POST',{'reason':'并发状态验证'});statuses=[a.result(),b.result()]
assert statuses in ([200,409],[404,200]),statuses
assert sql(f"SELECT status FROM ranking_reward_redemptions WHERE id={order['id']}") in ('FULFILLED','CANCEL_REQUESTED')
assert req('/api/rankings/admin/operations/reconciliations','POST',{})['anomalies']==0
assert req('/api/rankings/admin/recovery-drill')['schedulingEnabled'] is False
print('PASS: MySQL bulk-transfer full rollback, stale transfer preview, 999999999 refund boundary rollback/recovery, cross-student denial, concurrent fulfillment/cancellation state race and final reconciliation')

# Highest administrator retains historical-order processing after teacher offboarding.
phase3['restoreAdmin']()
teacherId=sql(f"SELECT id FROM users WHERE username='{target}'")
catalog=req('/api/admin/permissions')
codes=[r['code'] for group in catalog for r in group['permissions']]
req('/api/admin/users/'+teacherId+'/permissions','PUT',{'permissions':codes})
move=req(prefix+'/preview','POST',{'from':owner,'to':target,'studentIds':[student],'offboard':False,'reason':'历史订单最高管理员处理验证'})
req(prefix+'/'+move['previewId']+'/confirm','POST',{})
phase3['loginTeacher'](target,'isolated-teacher-test-123')
data,kind=state['multipart']({'name':'离职老师历史奖品','requiredPoints':1,'stock':3,'enabled':'true'})
legacyReward=req('/api/rankings/admin/rewards','POST',data,kind)
first=req('/api/rankings/admin/redemptions','POST',{'studentId':student,'rewardId':legacyReward['id']})
secondOrder=req('/api/rankings/admin/redemptions','POST',{'studentId':student,'rewardId':legacyReward['id']})
req('/api/rankings/admin/aftercare/legacy-orders',expected=(403,))
phase3['restoreAdmin']()
move=req(prefix+'/preview','POST',{'from':target,'to':owner,'studentIds':[],'offboard':True,'reason':'离职后订单处理验证'})
req(prefix+'/'+move['previewId']+'/confirm','POST',{})
currentBalance=state['own']()['availablePoints']
state['client']=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));state['csrf']='';state['csrf']=req('/api/auth/csrf')['token']
req('/api/public/rankings/student-auth/login','POST',{'phone':phone,'password':'student-new-test-123','studentId':student})
req('/api/public/rankings/student-redemptions/'+str(first['id'])+'/cancel-request','POST',{'reason':'离职历史退款'})
phase3['restoreAdmin']()
pending=req('/api/rankings/admin/aftercare/cancellations')
assert any(r['redemption_id']==first['id'] for r in pending)
refund=req('/api/rankings/admin/aftercare/cancellations/'+str(first['id'])+'/decision','POST',{'approved':True,'reason':'最高管理员接管历史退款'})
assert refund['creditedOwner']==owner and refund['creditedBalanceBefore']==currentBalance and refund['creditedBalanceAfter']==currentBalance+1,refund
assert state['own']()['availablePoints']==currentBalance+1
assert sql(f"SELECT stock FROM ranking_rewards WHERE id={legacyReward['id']}")=='2'
legacy=req('/api/rankings/admin/aftercare/legacy-orders')
assert any(r['id']==secondOrder['id'] for r in legacy)
req('/api/rankings/admin/redemptions/'+str(secondOrder['id'])+'/fulfillment','PATCH',{'fulfilled':True})
assert sql(f"SELECT status FROM ranking_reward_redemptions WHERE id={secondOrder['id']}")=='FULFILLED'
assert req('/api/rankings/admin/operations/reconciliations','POST',{})['anomalies']==0
print('PASS: offboarded teacher historical orders globally visible only to highest administrator; refund credited current owner with exact before/after evidence; stock restored once; fulfillment succeeds; ledger reconciles')
