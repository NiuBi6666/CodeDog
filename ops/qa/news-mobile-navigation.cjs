const {chromium}=require('/qa/node_modules/playwright');
const fs=require('fs'),path=require('path'),assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9223');
 try {
 for(const width of [375,390,430,1440]){
  const mobile=width<761;
  const context=await browser.newContext({viewport:{width,height:844},isMobile:mobile,hasTouch:mobile});
  try {
   const p=await context.newPage();const errors=[];p.on('pageerror',e=>errors.push(e.message));
   let listRequests=0;
   const rows=Array.from({length:60},(_,i)=>({id:i+1,source_name:'Test Publisher',category:'科技',lang:'en',title_en:'Independent report '+(i+1),title_zh:'独立报道第'+(i+1)+'条：科技与生活中的新发现',summary_zh:[],published_at:new Date(Date.UTC(2026,10,6,0,60-i)).toISOString(),translation_status:'ready',content_en:Array(18).fill('Original reporting with complete facts and quotations.').join('\n\n'),content_zh:Array(18).fill('新闻原文的中文翻译，包含事实和完整引述。').join('\n\n')}));
   await context.route('http://cdnews.test/**',async route=>{
    const url=new URL(route.request().url());
    if(url.pathname==='/api/news'){
     listRequests++;const start=Number(url.searchParams.get('cursor')||0);
     return route.fulfill({json:{articles:rows.slice(start,start+20),total:60,sources:[{source_name:'Test Publisher'}],has_more:start+20<60,next_cursor:String(start+20)}});
    }
    if(/^\/api\/news\/\d+$/.test(url.pathname))return route.fulfill({json:rows[Number(url.pathname.split('/').pop())-1]});
    if(url.pathname.startsWith('/api/'))return route.fulfill({json:{token:'test'}});
    const relative=url.pathname.replace(/^\/news\/?/,'');
    const file=relative.startsWith('assets/')||relative==='cdnews.svg'?path.join('/site',relative):'/site/index.html';
    return route.fulfill({path:file,contentType:file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':file.endsWith('.svg')?'image/svg+xml':'text/html'});
   });
   await p.goto('http://cdnews.test/news/');
   await p.waitForFunction(()=>document.querySelectorAll('.story-card').length>=20);
   await p.getByLabel('按来源筛选').selectOption('Test Publisher');
   await p.waitForFunction(()=>document.querySelectorAll('.story-card').length===20);
   for(const index of [14,34]){
    if(index===34){
     await p.locator('.story-card').nth(19).scrollIntoViewIfNeeded();
     await p.waitForFunction(()=>document.querySelectorAll('.story-card').length>=40);
    }
    const card=p.locator('.story-card').nth(index);
    await card.evaluate(el=>el.scrollIntoView({block:'center'}));
    await p.waitForTimeout(150);
    const before=await p.evaluate(m=>m?window.scrollY:document.querySelector('.feed-panel').scrollTop,mobile);
    const requestsBefore=listRequests;
    await card.click();
    await p.waitForURL('**/article/'+(index+1));
    await p.locator('.reader-content>h1').waitFor();
    assert.equal(await p.getByText('返回新闻列表',{exact:false}).count(),0);
    if(mobile)await p.waitForFunction(()=>window.scrollY===0);
    await p.evaluate(()=>window.scrollTo(0,300));
    await p.goBack();
    await p.waitForURL('http://cdnews.test/news/');
    await p.waitForFunction(({mobile,before})=>Math.abs((mobile?window.scrollY:document.querySelector('.feed-panel').scrollTop)-before)<=2,{mobile,before});
    assert.equal(await p.getByLabel('按来源筛选').inputValue(),'Test Publisher');
    assert.equal(listRequests,requestsBefore,'Returning must not reload/reset the feed');
    const after=await p.evaluate(m=>m?window.scrollY:document.querySelector('.feed-panel').scrollTop,mobile);
    console.log(JSON.stringify({width,article:index+1,before,after,loaded:await p.locator('.story-card').count()}));
   }
   assert.deepEqual(errors,[]);
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   await p.screenshot({path:'/qa/news-mobile-return-'+width+'-20261106.png'});
  } finally {await context.close();}
 }
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
