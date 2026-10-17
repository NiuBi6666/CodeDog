const {chromium}=require('/qa/node_modules/playwright');
(async()=>{const b=await chromium.connectOverCDP('http://127.0.0.1:9223'),ctx=await b.newContext({ignoreHTTPSErrors:true,viewport:{width:1440,height:1000}}),p=await ctx.newPage();const errors=[];p.on('pageerror',e=>errors.push(e.message));let previews=0,confirmed=0,attempts=0;
await ctx.route('**/api/**',async r=>{const path=new URL(r.request().url()).pathname;let v=[];
if(path==='/api/auth/me')v={username:'admin',admin:true,permissions:[]};
else if(path==='/api/auth/csrf')v={token:'qa'};
else if(path.endsWith('/student-auth/session'))return r.fulfill({status:401,json:{error:'请先登录'}});
else if(path.endsWith('/student-auth/login')){attempts++;return r.fulfill({status:409,json:{error:'该手机号关联多个账号，请补充学生ID'}});}
else if(path.endsWith('/board'))v={rankings:[]};
else if(path.endsWith('/completeness'))v={pendingAccounts:[],missingPhones:[],missingLessons:[],importErrors:[],missingDates:[]};
else if(path.endsWith('/transfers'))v={ownerships:[{student_id:'qa1',student_name:'测试学员',owner_username:'admin',state:'ACTIVE'}],teachers:[{username:'admin',enabled:true},{username:'teacher',enabled:true}]};
else if(path.endsWith('/transfers/preview')){previews++;v={previewId:'qa',from:'admin',to:'teacher',rows:[{studentId:'qa1',balance:10}]};}
else if(path.endsWith('/confirm')){confirmed++;v={count:1};}
await r.fulfill({status:200,json:v});});
try{await p.goto('https://127.0.0.1:9443/rankings');}catch(e){if(!String(e).includes('ERR_NETWORK_CHANGED'))throw e;await p.goto('https://127.0.0.1:9443/rankings');}await p.getByRole('button',{name:'交接与数据完整性',exact:true}).click();await p.getByLabel('原老师',{exact:true}).selectOption('admin');await p.getByLabel('接收老师',{exact:true}).selectOption('teacher');await p.getByLabel('学生ID（逗号分隔）',{exact:true}).fill('qa1');await p.getByLabel('原因 / 核实依据',{exact:true}).fill('确认转班');await p.getByRole('button',{name:'预览交接',exact:true}).click();await p.getByRole('button',{name:'确认交接 1 位学员',exact:true}).click();await p.getByRole('button',{name:'确认',exact:true}).click();if(previews!==1||confirmed!==1)throw Error('Transfer confirmation mismatch');
await p.screenshot({animations:'disabled',path:'/qa/continuity-desktop.png'});await p.setViewportSize({width:390,height:844});if(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Mobile overflow');await p.screenshot({animations:'disabled',path:'/qa/continuity-mobile.png'});
await p.goto('https://127.0.0.1:9443/ranking-board');await p.getByLabel('手机号',{exact:true}).fill('19900000001');await p.getByLabel('密码',{exact:true}).fill('123456');await p.getByRole('button',{name:'进入积分榜',exact:true}).click();await p.getByLabel('学生ID',{exact:true}).waitFor();if(attempts!==1)throw Error('Unexpected login retry');if(errors.length)throw Error(errors.join(';'));console.log('PASS: transfer preview and confirmation, completeness empty state, mobile layout, shared-phone student ID prompt');await ctx.close();await b.close();})().catch(e=>{console.error(e);process.exit(1)});
