const {chromium}=require('/qa/node_modules/playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9223');
 try{for(const width of [390,1440]){
  const c=await browser.newContext({viewport:{width,height:900}}),p=await c.newPage(),errors=[];
  p.on('pageerror',e=>errors.push(e.message));
  for(const id of [4295,4209]){
   const started=Date.now();
   await p.goto('https://codedog.online/news/article/'+id);
   await p.waitForFunction(()=>document.querySelector('.article-cover-stage img')?.naturalWidth>0,null,{timeout:45000});
   const img=p.locator('.article-cover-stage img');
   assert((await img.getAttribute('src')).startsWith('/news/media/'));
   assert.equal(await p.locator('.article-cover-backdrop').getAttribute('src'),await img.getAttribute('src'));
   assert.equal(await p.getByText('图片暂不可用',{exact:true}).count(),0);
   const box=await p.locator('.article-cover-media').boundingBox();
   if(await p.locator('.article-cover-media.is-single').count()){
    assert.equal(await p.locator('.article-cover-backdrop,.gallery-controls').count(),0);
    assert((await img.boundingBox()).height<=480.1);
   }else assert(Math.abs(box.width/box.height-16/9)<.02);
   await p.getByRole('button',{name:'放大新闻图片',exact:true}).click();
   await p.getByRole('dialog',{name:'新闻图片大图'}).waitFor();
   await p.waitForFunction(()=>document.querySelector('dialog img')?.naturalWidth>0);
   await p.locator('.lightbox-canvas').dispatchEvent('wheel',{deltaY:-100});
   assert.equal(await p.locator('.lightbox-canvas.is-zoomed').count(),1);
   await p.keyboard.press('0');
   assert.equal(await p.locator('.lightbox-canvas.is-zoomed').count(),0);
   await p.keyboard.press('Escape');
   await p.getByRole('dialog',{name:'新闻图片大图'}).waitFor({state:'hidden'});
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   console.log('LIVE_CACHE_IMAGE_OK',id,width,Date.now()-started);
  }
  await p.locator('.article-cover').screenshot({path:'/qa/news-cache-live-'+width+'.png'});
  assert.deepEqual(errors,[]);await c.close();
 }}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
