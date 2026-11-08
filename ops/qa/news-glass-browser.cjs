const {chromium}=require('/qa/node_modules/playwright');
const assert=require('node:assert/strict'),path=require('node:path');
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9223');
 try{
 for(const width of [320,375,390,430,768,1024,1280,1440]){
  const c=await browser.newContext({viewport:{width,height:900},reducedMotion:'reduce'}),p=await c.newPage(),errors=[];
  p.on('pageerror',e=>errors.push(e.message));
  await p.emulateMedia({reducedMotion:'reduce'});
  await c.route('http://cdnews.test/**',async r=>{
   const u=new URL(r.request().url());
   if(u.pathname.startsWith('/api/')){
    const response=await r.fetch({url:'https://codedog.online'+u.pathname+u.search});
    return r.fulfill({response});
   }
   const rel=u.pathname.replace(/^\/news\/?/,'');
   const f=rel.startsWith('assets/')||rel==='cdnews.svg'?path.join('/site',rel):'/site/index.html';
   return r.fulfill({path:f,contentType:f.endsWith('.js')?'application/javascript':f.endsWith('.css')?'text/css':f.endsWith('.svg')?'image/svg+xml':'text/html'});
  });
  await p.goto((process.env.QA_ORIGIN||'http://cdnews.test')+'/news/');
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
  assert.deepEqual(errors,[]);console.log('GLASS_LAYOUT_OK',width);await c.close();
 }
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
