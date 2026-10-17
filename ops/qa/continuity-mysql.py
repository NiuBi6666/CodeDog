import runpy,time,urllib.request,http.cookiejar,concurrent.futures
g=runpy.run_path(str(__import__('pathlib').Path(__file__).with_name('aftercare-mysql.py')));req=g['req'];state=req.__globals__;student=g['student'];admin=g['teacher'];adminCsrf=g['teacherCsrf'];prefix='/api/rankings/admin/transfers'
username=state['env'].get('ADMIN_USERNAME','admin')
def restoreAdmin():state['client']=admin;state['csrf']=adminCsrf
def loginTeacher(name,password):
 client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));state['client']=client;state['csrf']='';token=req('/api/auth/csrf')['token'];state['csrf']=token;req('/api/auth/login','POST',{'username':name,'password':password});return client,token
restoreAdmin();suffix=str(time.time_ns())[-12:];teacherName='handover'+suffix;password='isolated-teacher-test-123'
teacher=req('/api/admin/users','POST',{'username':teacherName,'password':password,'confirmation':password})
req(f'/api/admin/users/{teacher["id"]}/permissions','PUT',{'permissions':['rankings.view','rankings.board.read','rankings.redemptions.read','rankings.cancellations.read','rankings.cancellations.decide','rankings.completeness.read','rankings.expected.create','rankings.expected.edit','rankings.expected.delete','rankings.accounts.edit']})
data,kind=state['multipart']({'name':'交接历史订单','requiredPoints':1,'stock':2,'enabled':'true'});reward=req('/api/rankings/admin/rewards','POST',data,kind)
order=req('/api/rankings/admin/redemptions','POST',{'studentId':student,'rewardId':reward['id']});originalBalance=state['own']()['availablePoints']
preview=req(prefix+'/preview','POST',{'from':username,'to':teacherName,'studentIds':[student],'offboard':False,'reason':'隔离环境交接测试'})
assert state['own']()['availablePoints']==originalBalance
def confirm():return req(prefix+'/'+preview['previewId']+'/confirm','POST',{},expected=(200,409))
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda _:confirm(),range(2)))
assert sum('count' in r for r in results)==1,results
assert student not in [r['studentId'] for r in state['board']()['rankings']]
assert state['preview']()['hasErrors'],'Old owner import should be rejected'
# Student's prior session revoked; new login chooses new current owner and retains password.
state['client']=g['studentClient'];state['csrf']=g['studentCsrf'];req('/api/public/rankings/student-auth/session',expected=(401,))
profile=req('/api/public/rankings/student-auth/login','POST',{'phone':g['phone'],'password':'student-new-test-123'});assert profile['ownerUsername']==teacherName
assert req('/api/public/rankings/student-balance')['availablePoints']==originalBalance
assert any(r['id']==order['id'] for r in req('/api/public/rankings/student-redemptions'))
req(f'/api/public/rankings/student-redemptions/{order["id"]}/cancel-request','POST',{'reason':'交接后取消原老师订单'})
restoreAdmin();req(f'/api/rankings/admin/aftercare/cancellations/{order["id"]}/decision','POST',{'approved':True,'reason':'原老师处理历史订单退款'})
report=req('/api/rankings/admin/operations/reconciliations','POST',{});assert report['anomalies']==0
tc,token=loginTeacher(teacherName,password);board=req('/api/rankings/admin/board');current=next(r for r in board['rankings'] if r['studentId']==student);assert current['availablePoints']==originalBalance+1
assert all(r['studentId']==student for r in board['rankings'])
req(prefix,expected=(403,));req('/api/rankings/admin/aftercare/cancellations/'+str(order['id'])+'/decision','POST',{'approved':True,'reason':'跨老师拒绝'},expected=(409,))
body={'campId':current['campId'],'classId':current['classId'],'lessonId':'expected','lessonName':'待核实课程','plannedAt':'2026-01-01T00:00:00Z'}
req('/api/rankings/admin/expected-lessons','POST',body);panel=req('/api/rankings/admin/completeness');assert any(r['student_id']==student for r in panel['missingLessons']);row=req('/api/rankings/admin/expected-lessons')[0];body['lessonName']='修改后的预期课节';req('/api/rankings/admin/expected-lessons/'+str(row['id']),'PUT',body);req('/api/rankings/admin/expected-lessons/'+str(row['id']),'DELETE')
# Offboard atomically returns student, disables teacher and immediately revokes existing session.
restoreAdmin();p=req(prefix+'/preview','POST',{'from':teacherName,'to':username,'studentIds':[],'offboard':True,'reason':'离职交接验证'})
# Returning to an old account has no collision: stable account was moved, historical net balance is zero.
req(prefix+'/'+p['previewId']+'/confirm','POST',{})
state['client']=tc;state['csrf']=token;req('/api/rankings/admin/board',expected=(403,));req('/api/auth/me',expected=(401,))
restoreAdmin();assert req('/api/rankings/admin/operations/reconciliations','POST',{})['anomalies']==0
assert state['own']()['availablePoints']==originalBalance+1
print('PASS: MySQL transfer preview, atomic/repeated confirmation, old import rejected, session revoked, stable password, current teacher rankings, historical order visibility and refund routing, completeness CRUD, teacher offboarding, reconciled balances')

# Real MySQL shared phone: two distinct stable accounts, ambiguity does not expose account list.
originalStudent=state['student'];second='qa-shared-'+str(time.time_ns());state['student']=second
try:state['confirm'](state['preview']())
finally:state['student']=originalStudent
req('/api/rankings/admin/students/'+second+'/phone','PUT',{'phone':g['phone'],'expectedPhone':''})
state['client']=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));state['csrf']='';state['csrf']=req('/api/auth/csrf')['token']
ambiguous=req('/api/public/rankings/student-auth/login','POST',{'phone':g['phone'],'password':'123456'},expected=(409,));assert second not in str(ambiguous) and originalStudent not in str(ambiguous)
profile=req('/api/public/rankings/student-auth/login','POST',{'phone':g['phone'],'password':'123456','studentId':second});assert profile['studentId']==second
print('PASS: MySQL shared phone, ambiguity requires ID without exposing names or IDs')
