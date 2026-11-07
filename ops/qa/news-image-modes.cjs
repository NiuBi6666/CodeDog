const {chromium}=require('/qa/node_modules/playwright');
const assert=require('node:assert/strict'),path=require('path');
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9223');
 const shapes=[[1600,900],[900,1400],[1000,1000]];
 const rows=shapes.map(([w,h],i)=>({id:i+1,title_en:['A photographer at work','The complete portrait','A square image'][i],title_zh:['摄影师的新作','完整的人物肖像','方形新闻图片'][i],category:'科技',source_name:'Test News',published_at:'2026-11-07T01:00:00Z',lang:'en',translation_status:'ready',cover_image:'https://image.test/'+(i+1)+'.svg',content_en:'The original reporting begins here.',content_zh:'新闻正文从这里开始。'}));
 rows.push({...rows[0],id:4,cover_image:'https://image.test/fail.svg'},{...rows[0],id:5,cover_image:null});
 try{
 for(const width of [375,390,430,1440]){
  const c=await browser.newContext({viewport:{width,height:900}}),p=await c.newPage(),errors=[];
  p.on('pageerror',e=>errors.push(e.message));
  await c.route('https://wsrv.nl/**',async r=>{
   const src=new URL(r.request().url()).searchParams.get('url');
   if(src.endsWith('fail.svg'))return r.fulfill({status:404,body:'Not found'});
   const [w,h]=shapes[Number(src.match(/(\d+)\.svg$/)[1])-1];
   return r.fulfill({contentType:'image/svg+xml',body:`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}"><rect width="${w}" height="${h}" fill="#c8d4d5"/><rect x="24" y="24" width="${w-48}" height="${h-48}" fill="none" stroke="#385c6f" stroke-width="16"/><circle cx="${w/2}" cy="${h*.28}" r="${Math.min(w,h)*.14}" fill="#688d9a"/><path d="M${w*.15} ${h*.9} Q${w/2} ${h*.2} ${w*.85} ${h*.9}" fill="#486d79"/><text x="${w/2}" y="${h-60}" text-anchor="middle" font-family="Georgia" font-size="40" fill="#fff">CDNews / Full frame</text></svg>`});
  });
  await c.route('http://cdnews.test/**',async r=>{
   const u=new URL(r.request().url());let data;
   if(u.pathname==='/api/news')data={articles:rows,total:rows.length,sources:[{source_name:'Test News'}],has_more:false};
   else if(/^\/api\/news\/\d+$/.test(u.pathname))data=rows[Number(u.pathname.split('/').pop())-1];
   else if(u.pathname.startsWith('/api/'))data={};
   if(data)return r.fulfill({json:data});
   const rel=u.pathname.replace(/^\/news\/?/,'');const file=rel.startsWith('assets/')||rel==='cdnews.svg'?path.join('/site',rel):'/site/index.html';
   return r.fulfill({path:file,contentType:file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':file.endsWith('.svg')?'image/svg+xml':'text/html'});
  });
  for(const [i,[w,h]] of shapes.entries()){
   await p.goto('http://cdnews.test/news/article/'+(i+1));
   const cover=p.locator('.article-cover'),img=p.locator('.article-cover-stage img');
   await img.waitFor();await p.waitForFunction(()=>document.querySelector('.article-cover-stage img')?.naturalWidth>0);
   if(i===0)assert.equal(await cover.getAttribute('data-image-mode'),'contain');
   await p.getByRole('button',{name:'完整显示',exact:true}).click();
   assert.equal(await img.evaluate(e=>getComputedStyle(e).objectFit),'contain');
   await p.getByRole('button',{name:'紧凑裁切',exact:true}).click();
   assert.equal(await img.evaluate(e=>getComputedStyle(e).objectFit),'cover');
   assert((await img.boundingBox()).height<=220);
   await p.getByRole('button',{name:'原始比例',exact:true}).click();
   const box=await img.boundingBox();assert(Math.abs(box.width/box.height-w/h)<.02);
   await p.reload();await cover.waitFor();assert.equal(await cover.getAttribute('data-image-mode'),'natural');
   await p.getByRole('button',{name:'完整显示',exact:true}).click();
   await p.getByRole('button',{name:'放大新闻图片',exact:true}).click();
   const dialog=p.getByRole('dialog',{name:'新闻图片大图'});await dialog.waitFor();
   assert(await dialog.evaluate(e=>e.open));
   await p.keyboard.press('Tab');assert(await dialog.evaluate(e=>e.contains(document.activeElement)));
   await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});await p.waitForFunction(()=>document.documentElement.style.overflow==='');
   assert.equal(await p.locator('html').evaluate(e=>e.style.overflow),'');
   assert(await p.getByRole('button',{name:'放大新闻图片',exact:true}).evaluate(e=>document.activeElement===e));
   await p.getByRole('button',{name:'全屏查看图片'}).click();await dialog.waitFor();
   await p.getByRole('button',{name:'关闭大图'}).click();await dialog.waitFor({state:'hidden'});
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   if(i===1)await p.screenshot({path:'/qa/news-image-modes-'+width+'.png',fullPage:true});
   console.log('IMAGE_MODES_OK',width,w+'x'+h);
  }
  await p.goto('http://cdnews.test/news/article/4');await p.getByText('图片暂不可用',{exact:true}).waitFor();assert.equal(await p.locator('.article-cover-tools').count(),0);
  await p.goto('http://cdnews.test/news/article/5');await p.locator('.paragraphs').waitFor();assert.equal(await p.locator('.article-cover').count(),0);
  assert.deepEqual(errors,[]);console.log('FALLBACK_AND_NO_COVER_OK',width);await c.close();
 }
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
