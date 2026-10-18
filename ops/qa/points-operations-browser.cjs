const {chromium}=require('/qa/node_modules/playwright');
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9223');const ctx=await browser.newContext({ignoreHTTPSErrors:true,viewport:{width:1440,height:1000}});const page=await ctx.newPage();let confirms=0;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const student={studentId:'qa1',studentName:'测试学员',rank:1,availablePoints:3,totalPoints:3,completionPoints:1,inclassPoints:1,homeworkPoints:1,adjustmentPoints:0,spentPoints:0,level:1,levelName:'石墨',className:'测试班',classId:'c',campId:'camp',trend:'NEW',rankChange:0,accuracyRate:100};
 await ctx.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;let value=[];
  if(path==='/api/auth/me')value={username:'admin',admin:true,permissions:[]};
  else if(path==='/api/auth/csrf')value={token:'qa'};
  else if(path.endsWith('/board')||path.endsWith('/student-board'))value={rankings:[student],studentCount:1};
  else if(path.endsWith('/student-auth/session'))value={ownerUsername:'admin',studentId:'qa1',studentName:'测试学员',phone:'19900000001',mustChangePassword:false};
  else if(path.endsWith('/student-balance'))value={earnedPoints:3,spentPoints:0,adjustmentPoints:0,availablePoints:3};
  else if(path.endsWith('/announcement'))value={text:''};
  else if(path.endsWith('/import-previews'))value={previewId:'fixture',hasErrors:false,rows:[{studentId:'qa1',name:'测试学员',lesson:'第一课',before:0,after:3,error:''}]};
  else if(path.endsWith('/confirm')){confirms++;value={receivedRows:1,changedRows:1,unchangedRows:0,rejectedRows:0,errors:[]};}
  else if(path.endsWith('/ledger')||path.endsWith('/student-ledger'))value={items:[{id:1,event_type:'COURSE',delta:3,balance_before:0,balance_after:3,reason:'第一课',created_at:new Date().toISOString()}],total:1};
  else if(path.endsWith('/reconciliations'))value=[{id:1,student_id:'qa1',source_balance:3,ledger_balance:3,result:'OK',checked_at:new Date().toISOString()}];
  else if(path.endsWith('/import-batches'))value=[{id:1,source_name:'测试导入',status:'COMPLETED',changed_rows:1,rejected_rows:0,created_at:new Date().toISOString()}];
  await route.fulfill({status:200,json:value});
 });
 const base=process.env.QA_BASE||'https://127.0.0.1:9443';await page.goto(base+'/rankings');
 await page.locator('input[type=file][multiple]').setInputFiles({name:'fixture.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('mocked fixture')});
 await page.getByText('导入预览 · 1 条结果').waitFor();if(confirms)throw Error('Upload auto-confirmed');
 await page.getByRole('button',{name:'确认导入',exact:true}).click();await page.getByText('导入预览 · 1 条结果').waitFor({state:'detached'});if(confirms!==1)throw Error('Confirm not once');
 await page.getByRole('button',{name:'积分对账与导入记录',exact:true}).click();
 if(await page.getByLabel('选择学员查看积分明细').count())throw Error('Teacher ledger remains');
 await page.screenshot({path:'/qa/points-ops-desktop.png'});
 await page.setViewportSize({width:390,height:844});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Mobile overflow');await page.screenshot({path:'/qa/points-ops-mobile.png'});
 await page.goto(base+'/ranking-board');if(await page.getByRole('button',{name:'积分明细',exact:true}).count())throw Error('Student ledger remains');
 if(errors.length)throw Error(errors.join(';'));console.log('PASS: import preview/explicit confirmation, operations tab, removed ledger controls, desktop/mobile');await ctx.close();await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
