const {chromium}=require('/qa/node_modules/playwright');
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9223');const ctx=await browser.newContext({ignoreHTTPSErrors:true,viewport:{width:1440,height:1000}}),page=await ctx.newPage();let loggedIn=false,cancel=false,decisions=0,recoveries=0;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const person={studentId:'qa1',studentName:'测试学员',rank:1,availablePoints:3,totalPoints:3,completionPoints:1,inclassPoints:1,homeworkPoints:1,adjustmentPoints:0,spentPoints:0,level:1,levelName:'石墨',className:'测试班',classId:'c',campId:'camp',trend:'NEW',rankChange:0,accuracyRate:100};
 await ctx.route('**/api/**',async route=>{const p=new URL(route.request().url()).pathname;let v=[];
  if(p==='/api/auth/me')v={username:'admin',admin:true,permissions:[]};
  else if(p==='/api/auth/csrf')v={token:'qa'};
  else if(p.endsWith('/board')||p.endsWith('/student-board'))v={rankings:[person],studentCount:1};
  else if(p.endsWith('/student-auth/session')){if(!loggedIn)return route.fulfill({status:401,json:{error:'请先登录'}});v={ownerUsername:'admin',studentId:'qa1',studentName:'测试学员',phone:'19900000001',mustChangePassword:false};}
  else if(p.endsWith('/student-balance'))v={earnedPoints:3,spentPoints:0,availablePoints:3};
  else if(p.endsWith('/announcement'))v={text:''};
  else if(p.endsWith('/student-auth/recovery')){recoveries++;v={message:'申请已受理，请联系老师核实身份'};}
  else if(p.endsWith('/cancel-request')){cancel=true;v={accepted:true};}
  else if(p.endsWith('/student-redemptions'))v=[{id:1,studentId:'qa1',rewardName:'测试奖品',pointsSpent:1,balanceBefore:4,balanceAfter:3,status:cancel?'CANCEL_REQUESTED':'PENDING',redeemedAt:new Date().toISOString()}];
  else if(p.endsWith('/aftercare/cancellations'))v=[{redemption_id:1,student_name:'测试学员',reward_name:'测试奖品',reason:'误兑换',points_spent:1,status:decisions?'CANCELLED':'CANCEL_REQUESTED'}];
  else if(p.endsWith('/decision')){decisions++;v={status:'CANCELLED',refunded:true};}
  else if(p.endsWith('/aftercare/recoveries'))v=[{id:1,student_id:'qa1',student_name:'测试学员',owner_username:'admin',reason:'忘记密码',status:'PENDING_VERIFY'}];
  await route.fulfill({status:200,json:v});
 });
 const base=process.env.QA_BASE||'https://127.0.0.1:9443';await page.goto(base+'/ranking-board');await page.getByRole('button',{name:'找回密码',exact:true}).click();let dialog=page.getByRole('dialog',{name:'申请处理'});await dialog.getByLabel('手机号',{exact:true}).fill('19900000001');await dialog.getByLabel('学生ID',{exact:true}).fill('qa1');await dialog.getByRole('button',{name:'提交申请',exact:true}).click();await page.getByRole('button',{name:'知道了',exact:true}).click();if(recoveries!==1)throw Error('Recovery not requested');
 loggedIn=true;await page.reload();await page.getByRole('button',{name:'申请取消',exact:true}).click();await dialog.getByRole('button',{name:'提交申请',exact:true}).click();await page.getByRole('button',{name:'知道了',exact:true}).click();await page.getByText('取消申请待审批',{exact:true}).waitFor();
 await page.goto(base+'/rankings');await page.getByRole('button',{name:'取消兑换与账号找回',exact:true}).click();await page.getByLabel('处理说明',{exact:true}).fill('线下核实');await page.getByRole('button',{name:'同意退款',exact:true}).click();await page.getByRole('button',{name:'确认',exact:true}).click();await page.getByText('CANCELLED',{exact:true}).waitFor();if(decisions!==1)throw Error('Duplicate decision');
 await page.screenshot({path:'/qa/aftercare-desktop.png'});await page.setViewportSize({width:390,height:844});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Mobile overflow');await page.screenshot({path:'/qa/aftercare-mobile.png'});
 if(errors.length)throw Error(errors.join(';'));console.log('PASS: student recovery, student cancellation request, teacher confirmation, desktop/mobile');await ctx.close();await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
