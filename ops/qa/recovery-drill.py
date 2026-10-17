"""Server-only isolated restoration; never imports SQL into a production container."""
import subprocess,os,sys,time,uuid,json,tarfile,hashlib,shlex,re,io,datetime,tempfile,urllib.request,urllib.error,http.cookiejar
from pathlib import Path
ROOT=Path('/opt/codedog-ranking');BACKUPS=Path('/opt/codedog-backups')
def run(args,**kwargs):
 result=subprocess.run(args,capture_output=True,**kwargs)
 if result.returncode:raise RuntimeError('Isolated command failed: '+args[0]+' (exit '+str(result.returncode)+')')
 return result.stdout
def mysql(container,sql):
 assert container=='codedog-mysql' or container.startswith('codedog-drill-db-')
 return run(['docker','exec',container,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot "$MYSQL_DATABASE" -N -B -e "$1"','sh',sql],text=True).strip()
def hexsql(v):return "CONVERT(UNHEX('"+str(v).encode().hex()+"') USING utf8mb4)"
def publish(path):
 path=Path(path).resolve();assert path.parent.name.startswith('codedog-recovery-drill-') and path.parent.parent==Path('/home/ubuntu') and path.name=='report.json'
 report=json.loads(path.read_text());assert report['status']=='PASS' and report['productionUntouched'] and report['isolatedNetwork'] and not report['schedulingEnabled']
 actor=mysql('codedog-mysql',"SELECT username FROM users WHERE is_admin=TRUE AND enabled=TRUE ORDER BY id LIMIT 1");assert actor
 encoded=hexsql(json.dumps(report,ensure_ascii=False));started=hexsql(report['startedAt'].replace('T',' ').replace('Z',''));finished=hexsql(report['finishedAt'].replace('T',' ').replace('Z',''));point=hexsql(report['backupStartedAt'].replace('T',' ').replace('Z',''))
 mysql('codedog-mysql',"INSERT INTO ranking_recovery_drills(id,backup_name,started_at,finished_at,recovery_point,report_json,executed_by) VALUES("+','.join([hexsql(report['id']),hexsql(report['backupName']),started,finished,point,encoded,hexsql(actor)])+")")
 mysql('codedog-mysql',"INSERT INTO audit_log(action,actor_type,actor_id,actor_name,request_id,http_method,request_path,status_code,duration_ms,module,event_type,event_code,result,target_type,target_id,detail_json,ip_address,created_at) VALUES('recovery_drill_completed','TEACHER',"+hexsql(actor)+","+hexsql(actor)+","+hexsql(report['id'])+",'SYSTEM','ops://isolated-recovery-drill',200,0,'operations','execute','recovery_drill_completed','success','RECOVERY_DRILL',"+hexsql(report['id'])+","+encoded+",'SERVER',CURRENT_TIMESTAMP(6))")
 print(json.dumps({'published':report['id'],'status':'PASS','backup':report['backupName']},ensure_ascii=False))
if len(sys.argv)>1 and sys.argv[1]=='publish':publish(sys.argv[2]);sys.exit(0)
assert ROOT.is_dir() and ROOT.joinpath('.git').exists()
archive=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else max(BACKUPS.glob('codedog-*.tar.gz'),key=lambda q:q.stat().st_mtime).resolve()
assert archive.parent==BACKUPS and re.fullmatch(r'codedog-\d{8}T\d{6}Z.tar.gz',archive.name)
started=time.monotonic();startUtc=datetime.datetime.now(datetime.timezone.utc);stamp=lambda d:d.isoformat(timespec='microseconds').replace('+00:00','Z')
work=Path(tempfile.mkdtemp(prefix='codedog-recovery-drill-',dir='/home/ubuntu'));work.chmod(0o700)
identifier=str(uuid.uuid4());short=identifier[:8];network='codedog-drill-net-'+short;db='codedog-drill-db-'+short;app='codedog-drill-app-'+short
assert all(v.startswith('codedog-drill-') for v in (network,db,app))
report={'id':identifier,'status':'FAIL','backupName':archive.name,'backupStartedAt':stamp(datetime.datetime.strptime(archive.name,'codedog-%Y%m%dT%H%M%SZ.tar.gz').replace(tzinfo=datetime.timezone.utc)),'backupCompletedAt':stamp(datetime.datetime.fromtimestamp(archive.stat().st_mtime,datetime.timezone.utc)),'startedAt':stamp(startUtc),'productionUntouched':True,'isolatedNetwork':True,'schedulingEnabled':False}
try:
 sha=hashlib.sha256()
 with archive.open('rb') as stream:
  for chunk in iter(lambda:stream.read(1024*1024),b''):sha.update(chunk)
 report['archiveSha256']=sha.hexdigest()
 with tarfile.open(archive,'r:gz') as tar:
  for member in ('.env','source.tar.gz','mysql.sql'):assert tar.getmember(member).isfile()
  config=tar.extractfile('.env').read();assert len(config)<1048576
  envfile=work/'.env';envfile.write_bytes(config);envfile.chmod(0o600);report['configurationRestored']=hashlib.sha256(envfile.read_bytes()).digest()==hashlib.sha256(config).digest()
  cfg={}
  for line in config.decode().splitlines():
   line=line.strip()
   if not line or line.startswith('#'):continue
   key,sep,value=line.partition('=')
   if not sep:continue
   key=key.strip();value=value.strip()
   if value.startswith(('"',"'")):value=shlex.split(value,comments=True)[0] if value else ''
   else:value=value.split(' #',1)[0].strip()
   if re.fullmatch('[A-Z][A-Z0-9_]*',key) and key not in ('HOME','CODEX_HOME'):cfg[key]=value
  assert all(k in cfg for k in ('MYSQL_ROOT_PASSWORD','MYSQL_DATABASE','MYSQL_USER','MYSQL_PASSWORD','ADMIN_PASSWORD'))
  assert re.fullmatch('[A-Za-z0-9_]+',cfg['MYSQL_DATABASE'])
  source=tar.extractfile('source.tar.gz').read();report['sourceArchiveSha256']=hashlib.sha256(source).hexdigest()
  with tarfile.open(fileobj=io.BytesIO(source),mode='r:gz') as sources:
   names={m.name.removeprefix('./') for m in sources.getmembers() if m.isfile()};assert {'compose.yaml','backend/Dockerfile','frontend/Dockerfile'}.issubset(names)
   report['sourceFilesVerified']=len(names)
  run(['docker','network','create','--internal',network],text=True)
  environment=os.environ.copy();environment.update(cfg)
  args=['docker','run','-d','--name',db,'--network',network,'--memory','384m']
  for k in ('MYSQL_ROOT_PASSWORD','MYSQL_DATABASE','MYSQL_USER','MYSQL_PASSWORD'):args+=['-e',k]
  run(args+['mysql:8.4','--innodb-buffer-pool-size=32M','--performance-schema=OFF','--mysqlx=OFF'],env=environment,text=True)
  for attempt in range(60):
   try:mysql(db,'SELECT 1');break
   except RuntimeError:time.sleep(1)
  else:raise RuntimeError('Private MySQL did not start')
  restoreStarted=time.monotonic()
  importer=subprocess.Popen(['docker','exec','-i',db,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot "$MYSQL_DATABASE"'],stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
  with tar.extractfile('mysql.sql') as stream:
   for chunk in iter(lambda:stream.read(1024*1024),b''):importer.stdin.write(chunk)
  importer.stdin.close();stderr=importer.stderr.read();assert importer.wait()==0,'Backup SQL import failed'
  report['databaseRestoreSeconds']=round(time.monotonic()-restoreStarted,2)
 query="SELECT 'students',COUNT(*) FROM ranking_students UNION ALL SELECT 'accounts',COUNT(*) FROM ranking_student_accounts UNION ALL SELECT 'users',COUNT(*) FROM users UNION ALL SELECT 'orders',COUNT(*) FROM ranking_reward_redemptions UNION ALL SELECT 'images',COUNT(*) FROM ranking_rewards WHERE image_data IS NOT NULL UNION ALL SELECT 'ledgerEntries',COUNT(*) FROM ranking_point_ledger"
 countsBefore=mysql(db,query)
 accountFingerprint=mysql(db,"SET SESSION group_concat_max_len=4194304;SELECT SHA2(GROUP_CONCAT(SHA2(CONCAT_WS('|',id,owner_username,student_id,phone,password_hash,COALESCE(password_ciphertext,''),enabled,session_version),256) ORDER BY id SEPARATOR '|'),256) FROM ranking_student_accounts")
 imageFingerprint=mysql(db,"SET SESSION group_concat_max_len=4194304;SELECT SHA2(GROUP_CONCAT(CONCAT(id,':',SHA2(image_data,256),':',image_content_type) ORDER BY id SEPARATOR '|'),256) FROM ranking_rewards WHERE image_data IS NOT NULL")
 environment.update({'SPRING_DATASOURCE_URL':'jdbc:mysql://'+db+':3306/'+cfg['MYSQL_DATABASE']+'?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC','SPRING_DATASOURCE_USERNAME':cfg['MYSQL_USER'],'SPRING_DATASOURCE_PASSWORD':cfg['MYSQL_PASSWORD'],'CODEDOG_MAINTENANCE_ENABLED':'false','LEGACY_IMPORT_ENABLED':'false','CODEMAO_SESSION_COOKIE':'','JAVA_TOOL_OPTIONS':'-Xmx256m','COOKIE_SECURE':'false'})
 args=['docker','run','-d','--name',app,'--network',network,'--memory','384m']
 for k in sorted(set(cfg)|{'SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','CODEDOG_MAINTENANCE_ENABLED','LEGACY_IMPORT_ENABLED','CODEMAO_SESSION_COOKIE','JAVA_TOOL_OPTIONS','COOKIE_SECURE'}):args+=['-e',k]
 run(args+['codedog-backend'],env=environment,text=True)
 for attempt in range(60):
  try:
   response=run(['docker','exec',app,'wget','-qO-','http://127.0.0.1:8080/actuator/health'],text=True)
   if json.loads(response).get('status')=='UP':break
  except Exception:pass
  time.sleep(1)
 else:raise RuntimeError('Private restored backend did not become healthy')
 assert mysql(db,query)==countsBefore,'Business counts changed during startup'
 assert mysql(db,"SET SESSION group_concat_max_len=4194304;SELECT SHA2(GROUP_CONCAT(SHA2(CONCAT_WS('|',id,owner_username,student_id,phone,password_hash,COALESCE(password_ciphertext,''),enabled,session_version),256) ORDER BY id SEPARATOR '|'),256) FROM ranking_student_accounts")==accountFingerprint,'Account associations changed'
 assert mysql(db,"SET SESSION group_concat_max_len=4194304;SELECT SHA2(GROUP_CONCAT(CONCAT(id,':',SHA2(image_data,256),':',image_content_type) ORDER BY id SEPARATOR '|'),256) FROM ranking_rewards WHERE image_data IS NOT NULL")==imageFingerprint,'Reward images changed'
 orphans=mysql(db,"SELECT COUNT(*) FROM ranking_student_accounts a LEFT JOIN users u ON u.username=a.owner_username WHERE u.id IS NULL UNION ALL SELECT COUNT(*) FROM ranking_lesson_results r LEFT JOIN ranking_students s ON s.owner_username=r.owner_username AND s.camp_id=r.camp_id AND s.class_id=r.class_id AND s.student_id=r.student_id WHERE s.student_id IS NULL UNION ALL SELECT COUNT(*) FROM ranking_reward_redemptions d LEFT JOIN ranking_rewards r ON r.id=d.reward_id WHERE d.reward_id IS NOT NULL AND r.id IS NULL")
 assert all(int(v)==0 for v in orphans.splitlines()),'Dangling account/course/order references'
 mismatches=mysql(db,"SELECT COUNT(*) FROM (SELECT s.owner_username,s.student_id,COALESCE(e.p,0)+COALESCE(a.p,0)-COALESCE(d.p,0) source,COALESCE(l.p,0) ledger FROM (SELECT DISTINCT owner_username,student_id FROM ranking_students) s LEFT JOIN(SELECT owner_username,student_id,SUM(total_points) p FROM ranking_lesson_results GROUP BY owner_username,student_id)e ON e.owner_username=s.owner_username AND e.student_id=s.student_id LEFT JOIN(SELECT owner_username,student_id,SUM(points)p FROM ranking_point_adjustments GROUP BY owner_username,student_id)a ON a.owner_username=s.owner_username AND a.student_id=s.student_id LEFT JOIN(SELECT owner_username,student_id,SUM(points_spent)p FROM ranking_reward_redemptions GROUP BY owner_username,student_id)d ON d.owner_username=s.owner_username AND d.student_id=s.student_id LEFT JOIN(SELECT owner_username,student_id,SUM(delta)p FROM ranking_point_ledger GROUP BY owner_username,student_id)l ON l.owner_username=s.owner_username AND l.student_id=s.student_id) t WHERE t.source<>t.ledger OR t.source<0 OR t.source>999999999")
 assert int(mismatches)==0,'Restored points did not reconcile'
 clientCode="const fs=require('fs');const cfg=JSON.parse(fs.readFileSync(0,'utf8'));let token='';const cookies=new Map();async function req(path,body,expected=200){const headers={'Content-Type':'application/json'};if(token)headers['X-XSRF-TOKEN']=token;if(cookies.size)headers.Cookie=[...cookies].map(([k,v])=>k+'='+v).join('; ');const r=await fetch(cfg.base+path,{method:body===undefined?'GET':'POST',headers,body:body===undefined?undefined:JSON.stringify(body)});for(const h of r.headers.getSetCookie()){const pair=h.split(';')[0],pos=pair.indexOf('=');cookies.set(pair.slice(0,pos),pair.slice(pos+1));}if(r.status!==expected)throw Error('Restored API status check failed '+path+' '+r.status);const text=await r.text();return text?JSON.parse(text):null;}(async()=>{await req('/api/rankings/admin/board',undefined,401);await req('/api/public/rankings/student-board',undefined,401);token=(await req('/api/auth/csrf')).token;await req('/api/auth/login',{username:cfg.username,password:cfg.password});const runtime=await req('/api/rankings/admin/recovery-drill');if(runtime.schedulingEnabled!==false)throw Error('Scheduled tasks still enabled');const board=await req('/api/rankings/admin/board');if(!Array.isArray(board.rankings))throw Error('Invalid restored board');process.stdout.write(JSON.stringify({administratorLoginVerified:true,anonymousAccessDenied:true,schedulingEnabled:false,boardEntries:board.rankings.length}));})().catch(e=>{process.stderr.write(e.message);process.exit(1)});"
 clientResult=json.loads(run(['docker','run','--rm','-i','--network',network,'--memory','128m','node:22-alpine','node','--max-old-space-size=64','-e',clientCode],input=json.dumps({'base':'http://'+app+':8080','username':cfg.get('ADMIN_USERNAME','admin'),'password':cfg['ADMIN_PASSWORD']}).encode()))
 assert clientResult['administratorLoginVerified'] and clientResult['anonymousAccessDenied'] and clientResult['schedulingEnabled'] is False
 binding=json.loads(run(['docker','inspect',app],text=True))[0]['HostConfig']['PortBindings'] or {};assert all(b['HostIp']=='127.0.0.1' for bindings in binding.values() for b in bindings)
 net=json.loads(run(['docker','network','inspect',network],text=True))[0];assert net['Internal'] is True
 report.update({'status':'PASS','counts':dict((k,int(v)) for k,v in (line.split('\t') for line in countsBefore.splitlines())),'accountAssociationsVerified':True,'rewardImageBytesVerified':True,'ledgerMismatches':0,'orphanReferences':0,'anonymousAccessDenied':True,'administratorLoginVerified':True,'privateBackendHealthy':True,'externalSyncDisabled':True,'schedulingEnabled':False,'successfulMigrations':int(mysql(db,'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1'))})
except Exception as error:
 report['failure']=type(error).__name__+': '+str(error)[:200]
finally:
 cleanup=[]
 for name in (app,db):cleanup.append(subprocess.run(['docker','rm','-f','-v',name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode)
 cleanup.append(subprocess.run(['docker','network','rm',network],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode)
 if report['status']=='PASS' and any(cleanup):report['status']='FAIL';report['failure']='Temporary recovery resources could not be removed'
 if (work/'.env').exists():(work/'.env').unlink()
 report.update({'totalSeconds':round(time.monotonic()-started,2),'finishedAt':stamp(datetime.datetime.now(datetime.timezone.utc)),'temporaryContainersRemoved':not any(cleanup),'recoveredConfigCopyRemoved':True})
 (work/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));(work/'report.json').chmod(0o600)
 print(json.dumps({'reportPath':str(work/'report.json'),**report},ensure_ascii=False))
if report['status']!='PASS':sys.exit(1)
