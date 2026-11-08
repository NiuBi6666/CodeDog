const {chromium}=require('/qa/node_modules/playwright');
const assert=require('node:assert/strict'),path=require('node:path');
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9223');
 try{
 for(const width of [390,1440]){
  const c=await browser.newContext({viewport:{width,height:900},reducedMotion:'reduce'}),p=await c.newPage(),errors=[];
  p.on('pageerror',e=>errors.push(e.message));
  await p.emulateMedia({reducedMotion:'reduce'});
  await c.route('http://cdnews.test/**',async r=>{
   const u=new URL(r.request().url());
   if(u.pathname.startsWith('/api/')){
    const a={id:999,source_name:'News QA',category:'科技',lang:'en',title_en:'Independent reporting from around the world',title_zh:'来自全球的独立报道',content_en:'Original reporting with complete facts and quotations.',content_zh:'新闻原文的中文译文，包含事实和完整引述。',translation_status:'ready',published_at:'2026-11-08T02:00:00Z'};
    if(u.pathname==='/api/news')return r.fulfill({json:{articles:[a],sources:[],total:1,has_more:false}});
    if(u.pathname==='/api/auth/csrf')return r.fulfill({json:{token:'fixture'}});
    if(u.pathname==='/api/news/translate')return r.fulfill({json:{translation:'原始报道'}});
    return r.fulfill({json:a});
   }
   const rel=u.pathname.replace(/^\/news\/?/,'');
   const f=rel.startsWith('assets/')||rel==='cdnews.svg'?path.join('/site',rel):'/site/index.html';
   return r.fulfill({path:f,contentType:f.endsWith('.js')?'application/javascript':f.endsWith('.css')?'text/css':f.endsWith('.svg')?'image/svg+xml':'text/html'});
  });
  await p.goto('http://cdnews.test/news/');
  await p.locator('.story-card').first().waitFor();
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'feed overflow '+width);
  const nav=p.getByRole('navigation',{name:'领域分类'});
  assert.equal(await nav.locator('button').count(),8);
  const dimensions=await p.locator('.topbar').evaluate(e=>({w:e.clientWidth,scroll:e.scrollWidth}));
  assert(dimensions.scroll<=dimensions.w+2,'header overflow '+width);
  const glass=await p.locator('.topbar').evaluate(e=>getComputedStyle(e).backdropFilter);
  assert(glass.includes('blur(28px)'));
  await p.waitForFunction(()=>getComputedStyle(document.querySelector('.ambient-background'),'::before').animationName==='none');
  await p.locator('.story-card').first().click();
  await p.locator('.paragraphs').waitFor();
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'reader overflow '+width);
  await p.getByRole('button',{name:'EN',exact:true}).click();
  if(!await p.locator('.original-language-note').count()) await p.waitForFunction(()=>document.querySelectorAll('.zh-paragraph').length===0);
  await p.getByRole('button',{name:'双语',exact:true}).click();
  await p.getByRole('button',{name:'放大正文字号'}).click();
  await p.waitForFunction(()=>getComputedStyle(document.querySelector('.paragraphs')).fontSize==='18px');
  if(width===1440||width===390){
   await p.screenshot({path:'/qa/news-glass-reader-'+width+'.png',fullPage:width<761});
   await p.goBack();await p.locator('.story-card').first().waitFor();
   await p.screenshot({path:'/qa/news-glass-feed-'+width+'.png',fullPage:false});
  }
  await p.locator('.story-card').first().click();await p.locator('.en-paragraph').waitFor();
  await p.locator('.en-paragraph').first().evaluate(e=>{const r=document.createRange();r.setStart(e.firstChild,0);r.setEnd(e.firstChild,8);const s=getSelection();s.removeAllRanges();s.addRange(r);document.dispatchEvent(new Event('selectionchange'));});
  await p.getByText('原始报道',{exact:true}).waitFor();
  const box=await p.getByRole('dialog',{name:'划词翻译'}).boundingBox();assert(box.x>=0&&box.x+box.width<=width+1);
  await p.keyboard.press('Escape');await p.getByRole('dialog',{name:'划词翻译'}).waitFor({state:'hidden'});
  await p.keyboard.press('Tab');
  assert(await p.evaluate(()=>document.activeElement!==document.body));
  assert.deepEqual(errors,[]);console.log('GLASS_SELECTION_OK',width);await c.close();
 }
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
