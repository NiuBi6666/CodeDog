const {chromium}=require('/qa/node_modules/playwright');
const assert=require('node:assert/strict'),path=require('path');
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9223');
 const report=(id)=>({article_id:id,id,title_en:'Original report '+id,title_zh:'任务进展报道'+id,lang:'en',source_name:'BBC',origin_url:'https://example.com/'+id,published_at:'2026-11-07T01:00:00Z',translation_status:'ready',content_en:'Original report paragraph.',content_zh:'原文对应译文。',category:'科技',events:[{id:1,title:'卫星发射任务',article_count:2}]});
 const fixture={id:1,title:'卫星发射任务',category:'科技',status:'published',article_count:2,suggestions:[],nodes:[{id:10,title:'宣布发射计划',occurred_at:'2026-11-01T01:00:00Z',time_basis:'published',stage:'background',reports:[report(1)]},{id:11,title:'完成发射任务',occurred_at:'2026-11-06T01:00:00Z',time_basis:'occurred',stage:'outcome',reports:[report(2)]}]};
 try{
 for(const width of [375,390,430,1440]){
  const c=await browser.newContext({viewport:{width,height:900}});const p=await c.newPage(),errors=[];p.on('pageerror',e=>errors.push(e.message));
  await c.route('http://cdnews.test/**',async r=>{
   const u=new URL(r.request().url());let data;
   if(u.pathname==='/api/news/events')data={events:[fixture]};
   else if(u.pathname==='/api/news/events/1')data=fixture;
   else if(u.pathname==='/api/news')data={articles:[report(1),report(2)],sources:[{source_name:'BBC'}],total:2,has_more:false};
   else if(u.pathname.startsWith('/api/news/'))data=report(Number(u.pathname.split('/').pop()));
   if(data)return r.fulfill({json:data});
   const rel=u.pathname.replace(/^\/news\/?/,'');
   const file=rel.startsWith('assets/')||rel==='cdnews.svg'?path.join('/site',rel):'/site/index.html';
   await r.fulfill({path:file,contentType:file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':file.endsWith('.svg')?'image/svg+xml':'text/html'});
  });
  await p.goto('http://cdnews.test/news/');
  await p.locator('.story-events a').first().click();
  await p.waitForURL('**/event/1');
  await p.getByRole('heading',{name:'卫星发射任务',exact:true}).waitFor();
  assert.equal(await p.locator('.event-timeline>li').count(),2);
  assert((await p.locator('.event-timeline>li').first().innerText()).includes('宣布发射计划'));
  await p.getByRole('button',{name:'时间正序'}).click();
  assert((await p.locator('.event-timeline>li').first().innerText()).includes('完成发射任务'));
  assert.equal(await p.locator('.event-report a[target="_blank"]').count(),2);
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await p.locator('.event-report').first().getByRole('link',{name:'任务进展报道2',exact:true}).click();
  await p.waitForURL('**/article/2');await p.locator('.reader-content>h1').waitFor();
  await p.goBack();await p.waitForURL('**/event/1');
  await p.getByRole('link',{name:'全部事件',exact:true}).click();
  await p.waitForURL('**/events');
  await p.getByRole('heading',{name:'事件追踪',exact:true}).waitFor();
  await p.screenshot({path:'/qa/news-events-public-'+width+'.png',fullPage:true});
  assert.deepEqual(errors,[]);console.log('PUBLIC_TIMELINE_OK',width);await c.close();
 }
 const c=await browser.newContext({viewport:{width:1440,height:1000}}),p=await c.newPage();let associations=0,published=0;
 await c.route('http://console.test/**',async r=>{
  const u=new URL(r.request().url()),method=r.request().method();let data;
  if(u.pathname==='/api/auth/me')data={username:'event-qa',admin:false,permissions:['news.view','news.read','news.events.read','news.events.create','news.events.update','news.events.publish','news.events.associate']};
  else if(u.pathname==='/api/auth/csrf')data={token:'test'};
  else if(u.pathname==='/api/admin/news')data={configuration:{},sources:[],runs:[],batches:[]};
  else if(u.pathname==='/api/admin/news/events')data={events:[{...fixture,status:'draft',suggested_count:1}]};
  else if(u.pathname==='/api/admin/news/event-articles')data={articles:[report(3)]};
  else if(u.pathname==='/api/admin/news/events/1/status'){published++;data=fixture;}
  else if(u.pathname==='/api/admin/news/events/1/articles/3'){associations++;data={...fixture,status:'draft'};}
  else if(u.pathname==='/api/admin/news/events/1')data={...fixture,status:'draft'};
  else if(u.pathname.startsWith('/api/'))data={};
  if(data)return r.fulfill({json:data});
  const rel=u.pathname.slice(1),file=rel.startsWith('assets/')?path.join('/admin',rel):'/admin/index.html';
  return r.fulfill({path:file,contentType:file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html'});
 });
 await p.goto('http://console.test/newsconfig');
 await p.getByRole('tab',{name:'事件追踪'}).click();
 await p.locator('.news-events-list>button').first().click();
 await p.getByLabel('查找报道',{exact:true}).fill('report');
 await p.getByRole('button',{name:'搜索',exact:true}).click();
 await Promise.all([p.waitForResponse(r=>r.url().includes('/articles/3')),p.getByRole('button',{name:'关联报道',exact:true}).click()]);await p.waitForFunction(()=>!Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='发布')?.disabled);assert.equal(associations,1);
 await Promise.all([p.waitForResponse(r=>r.url().endsWith('/status')),p.getByRole('button',{name:'发布',exact:true}).click()]);assert.equal(published,1);
 await p.screenshot({path:'/qa/news-events-admin-1440.png',fullPage:true});
 console.log('ADMIN_CORRECTION_AND_PUBLISH_OK');await c.close();
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
