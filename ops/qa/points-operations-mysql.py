import urllib.request,urllib.error,http.cookiejar,json,subprocess,io,zipfile,time,concurrent.futures
from xml.sax.saxutils import escape
base='http://127.0.0.1:18090'
assert base=='http://127.0.0.1:18090'
env=dict(line.split('=',1) for line in subprocess.run(['docker','exec','codedog-ops-backend','printenv'],capture_output=True,text=True,check=True).stdout.splitlines() if '=' in line)
jar=http.cookiejar.CookieJar();client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
csrf=''
def req(path,method='GET',data=None,kind='application/json',expected=(200,201)):
 if data is not None and not isinstance(data,bytes):data=json.dumps(data).encode()
 headers={'Content-Type':kind}
 if csrf:headers['X-XSRF-TOKEN']=csrf
 request=urllib.request.Request(base+path,data=data,headers=headers,method=method)
 try:
  with client.open(request,timeout=30) as response:status=response.status;body=response.read()
 except urllib.error.HTTPError as error:status=error.code;body=error.read()
 assert status in expected,(path,status,body[:1200])
 return json.loads(body) if body else None
csrf=req('/api/auth/csrf')['token']
req('/api/auth/login','POST',{'username':env.get('ADMIN_USERNAME','admin'),'password':env['ADMIN_PASSWORD']})
student='qa-ops-'+str(time.time_ns());name='事务测试学员'
def workbook(completed='是'):
 headers={'A':'用户ID','B':'姓名','L':'班级名称','M':'课程名称','P':'是否完课','U':'课中OJ题总数','W':'课中OJ题通过数','X':'课中客观题总数','Z':'课中客观题通过数','AF':'课后作业OJ题总数','AH':'课后作业OJ题通过数','AI':'课后作业客观题总数','AK':'课后作业客观题通过数'}
 values={k:'0' for k in headers};values.update(A=student,B=name,L='事务测试班',M='第一课',P=completed)
 def row(values,n):return '<row r="%s">'%n+''.join('<c r="%s%s" t="inlineStr"><is><t>%s</t></is></c>'%(k,n,escape(v))for k,v in values.items())+'</row>'
 files={'[Content_Types].xml':'<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>',
 '_rels/.rels':'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>',
 'xl/workbook.xml':'<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="测试" sheetId="1" r:id="rId1"/></sheets></workbook>',
 'xl/_rels/workbook.xml.rels':'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>',
 'xl/worksheets/sheet1.xml':'<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'+row(headers,1)+row(values,2)+'</sheetData></worksheet>'}
 out=io.BytesIO()
 with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
  for k,v in files.items():z.writestr(k,v)
 return out.getvalue()
def multipart(fields,files=None):
 boundary='codedog-fixture-'+str(time.time_ns());chunks=[]
 for key,value in fields.items():chunks.append(('--'+boundary+'\r\nContent-Disposition: form-data; name="'+key+'"\r\n\r\n'+str(value)+'\r\n').encode())
 for key,filename,value in files or []:chunks.extend([('--'+boundary+'\r\nContent-Disposition: form-data; name="'+key+'"; filename="'+filename+'"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode(),value,b'\r\n'])
 chunks.append(('--'+boundary+'--\r\n').encode());return b''.join(chunks),'multipart/form-data; boundary='+boundary
prefix='/api/rankings/admin/operations'
def preview(done='是'):
 data,kind=multipart({},[('files','fixture.xlsx',workbook(done))]);return req(prefix+'/import-previews','POST',data,kind)
def confirm(p,expected=(200,)):return req(prefix+'/import-previews/'+p['previewId']+'/confirm','POST',{},expected=expected)
def board():return req('/api/rankings/admin/board')
def own():return next(r for r in board()['rankings'] if r['studentId']==student)
def adjust(value):
 r=own();req('/api/rankings/admin/students/'+student+'/points','PUT',{'campId':r['campId'],'classId':r['classId'],'targetPoints':value,'reason':'隔离环境事务校验'})
count=len(board()['rankings']);p=preview();assert not p['hasErrors'];assert len(board()['rankings'])==count
first=confirm(p);assert own()['availablePoints']==3;confirm(p,(409,))
same=confirm(preview());assert same['changedRows']==0 and same['unchangedRows']==1;assert own()['availablePoints']==3
adjust(20)
data,kind=multipart({'name':'并发测试奖品','requiredPoints':5,'stock':1,'enabled':'true'})
reward=req('/api/rankings/admin/rewards','POST',data,kind)
def redeem():
 try:return req('/api/rankings/admin/redemptions','POST',{'studentId':student,'rewardId':reward['id']},expected=(201,422))
 except AssertionError:raise
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda _:redeem(),range(2)))
success=[r for r in results if 'id'in r];assert len(success)==1,results
assert own()['availablePoints']==15
adjust(0);req(prefix+f'/import-batches/{first["batchId"]}/reverse','POST',{'reason':'余额不足时整批拒绝'},expected=(409,));assert own()['availablePoints']==0
adjust(20);req(prefix+f'/import-batches/{first["batchId"]}/reverse','POST',{'reason':'测试正常冲正'});assert own()['availablePoints']==17
req(prefix+f'/import-batches/{first["batchId"]}/reverse','POST',{'reason':'测试重复撤销'},expected=(409,))
a=confirm(preview());b=confirm(preview('否'));assert b['changedRows']==1
req(prefix+f'/import-batches/{a["batchId"]}/reverse','POST',{'reason':'后续覆盖拒绝'},expected=(409,))
stale=preview();confirm(preview('是'));confirm(stale,(409,))
report=req(prefix+'/reconciliations','POST',{});assert report['anomalies']==0,report
ledger=req(prefix+'/students/'+student+'/ledger');assert len(ledger['items'])>=6
print('PASS: MySQL migration, preview without score changes, confirmation, duplicate import, concurrent last-stock redemption, negative-balance reversal rollback, reversal, duplicate reversal, overwritten result conflict, stale preview, ledger reconciliation')
