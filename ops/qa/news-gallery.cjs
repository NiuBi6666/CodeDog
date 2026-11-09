const {chromium}=require('/qa/node_modules/playwright');
const assert=require('node:assert/strict'),path=require('path');
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9223');
 const shapes=[[1600,900],[900,1400],[1000,1000]];
 const rows=shapes.map(([w,h],i)=>({id:i+1,origin_url:'https://publisher.test/article/'+(i+1),title_en:['A photographer at work','The complete portrait','A square image'][i],title_zh:['摄影师的新作','完整的人物肖像','方形新闻图片'][i],category:'科技',source_name:'Test News',published_at:'2026-11-07T01:00:00Z',lang:'en',translation_status:'ready',cover_image:'https://image.test/'+(i+1)+'.svg',content_en:'The original reporting begins here.',content_zh:'新闻正文从这里开始。'}));
 rows.push({...rows[0],id:6,images:shapes.map((_,i)=>'https://image.test/'+(i+1)+'.svg')});
 rows.push({...rows[0],id:4,cover_image:'https://image.test/fail.svg'},{...rows[0],id:5,cover_image:null});
 try{
 for(const width of [375,390,430,1440]){
  const c=await browser.newContext({viewport:{width,height:900}}),p=await c.newPage(),errors=[];
  p.on('pageerror',e=>errors.push(e.message));
  await c.addInitScript(()=>localStorage.setItem('cdnews.cover-mode','compact'));
  await c.route('https://wsrv.nl/**',async r=>{
   const src=new URL(r.request().url()).searchParams.get('url');
   if(src.endsWith('fail.svg'))return r.fulfill({status:404,body:'Not found'});
   await new Promise(resolve=>setTimeout(resolve,700));
   const [w,h]=shapes[Number(src.match(/(\d+)\.svg$/)[1])-1];
   return r.fulfill({headers:{'Cache-Control':'public,max-age=3600'},contentType:'image/svg+xml',body:`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}"><rect width="${w}" height="${h}" fill="#c8d4d5"/><rect x="24" y="24" width="${w-48}" height="${h-48}" fill="none" stroke="#385c6f" stroke-width="16"/><circle cx="${w/2}" cy="${h*.28}" r="${Math.min(w,h)*.14}" fill="#688d9a"/><path d="M${w*.15} ${h*.9} Q${w/2} ${h*.2} ${w*.85} ${h*.9}" fill="#486d79"/><text x="${w/2}" y="${h-60}" text-anchor="middle" font-family="Georgia" font-size="40" fill="#fff">CDNews / Full frame</text></svg>`});
  });
  await c.route('http://cdnews.test/**',async r=>{
   const u=new URL(r.request().url());let data;
   if(u.pathname==='/api/news')data={articles:rows,total:rows.length,sources:[{source_name:'Test News'}],has_more:false};
   else if(/^\/api\/news\/\d+$/.test(u.pathname))data=rows.find(a=>a.id===Number(u.pathname.split('/').pop()));
   else if(u.pathname.startsWith('/api/'))data={};
   if(data)return r.fulfill({json:data});
   const rel=u.pathname.replace(/^\/news\/?/,'');const file=rel.startsWith('assets/')||rel==='cdnews.svg'?path.join('/site',rel):'/site/index.html';
   return r.fulfill({path:file,contentType:file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':file.endsWith('.svg')?'image/svg+xml':'text/html'});
  });
  await p.goto('http://cdnews.test/news/article/6');
  for(const [i,[w,h]] of shapes.entries()){
   const img=p.locator('.article-cover-stage img');
   await p.waitForFunction(expected=>document.querySelector('.article-cover-stage img')?.naturalWidth===expected,w);
   const background=p.locator('.article-cover-backdrop');
   assert.equal(await background.getAttribute('src'),await img.getAttribute('src'));
   assert.equal(await background.getAttribute('aria-hidden'),'true');
   const material=await background.evaluate(e=>({fit:getComputedStyle(e).objectFit,filter:getComputedStyle(e).filter,pointer:getComputedStyle(e).pointerEvents}));
   assert.equal(material.fit,'cover');assert(material.filter.includes('blur(30px)'));assert.equal(material.pointer,'none');
   assert.equal(await img.evaluate(e=>getComputedStyle(e).objectFit),'contain');
   const box=await p.locator('.article-cover-media').boundingBox();
   const bgBox=await background.boundingBox();assert(bgBox.width>box.width&&bgBox.height>box.height);assert(Math.abs(box.width/box.height-16/9)<.02);
   const arrows=await p.locator('.gallery-controls button').all();
   for(const arrow of arrows){const a=await arrow.boundingBox();assert(a.x>=box.x&&a.x+a.width<=box.x+box.width+1);assert(Math.abs(a.y+a.height/2-box.y-box.height/2)<2);}
   if(i===0)globalThis.initialFrame=box;
   else assert(Math.abs(box.height-globalThis.initialFrame.height)<1,'switching image must not change frame height');
   await p.getByText((i+1)+' / 3',{exact:true}).waitFor();
   const before=await p.locator('.article-cover-stage img').getAttribute('src');
   await p.getByRole('button',{name:'下一张图片',exact:true}).click();
   assert(await p.locator('.article-cover-stage img').evaluate(e=>e.clientWidth>0&&e.clientHeight>0),'image must stay visible while switching');
  }
  await p.getByText('1 / 3',{exact:true}).waitFor();
  await p.getByRole('button',{name:'上一张图片',exact:true}).click();
  await p.getByText('3 / 3',{exact:true}).waitFor();
  await p.getByRole('button',{name:'放大新闻图片',exact:true}).click();
  await p.keyboard.press('ArrowRight');await p.getByText('1 / 3',{exact:true}).waitFor();
  await p.keyboard.press('Escape');
  if(width<761){
   await p.locator('.article-cover-media').dispatchEvent('touchstart',{touches:[{identifier:1,clientX:260,clientY:200}]});
   await p.locator('.article-cover-media').dispatchEvent('touchend',{changedTouches:[{identifier:1,clientX:80,clientY:205}]});
   await p.getByText('2 / 3',{exact:true}).waitFor();
  }
  console.log('GALLERY_NAVIGATION_AND_RATIO_OK',width);
  for(const [i,[w,h]] of shapes.entries()){
   await p.goto('http://cdnews.test/news/article/'+(i+1));
   const cover=p.locator('.article-cover'),img=p.locator('.article-cover-stage img');
   await img.waitFor();await p.waitForFunction(()=>document.querySelector('.article-cover-stage img')?.naturalWidth>0);
   assert.equal(await cover.locator('select,.article-cover-tools,.article-cover-expand').count(),0);
   assert.equal(await img.evaluate(e=>getComputedStyle(e).objectFit),'contain');
   assert.equal(await p.locator('.reader-toolbar .reader-category').innerText(),'科技');
   assert.equal(await p.locator('.reader-toolbar .reader-source').innerText(),'Test News');
   assert.equal(await p.locator('.reader-toolbar time').count(),1);
   assert.equal(await p.getByRole('link',{name:'查看原文',exact:true}).count(),1);
   const tools=await p.locator('.reading-tools').boundingBox();
   assert(tools.x>=0&&tools.x+tools.width<=width,'tools must fit the viewport');
   const fitted=await img.boundingBox(),frame=await p.locator('.article-cover-stage').boundingBox();
   assert(Math.abs(fitted.width-frame.width)<2&&Math.abs(fitted.height-frame.height)<2);
   assert(Math.abs(frame.width/frame.height-16/9)<.02);
   await p.getByRole('button',{name:'放大新闻图片',exact:true}).click();
   const dialog=p.getByRole('dialog',{name:'新闻图片大图'});await dialog.waitFor();
   assert(await dialog.evaluate(e=>e.open));
   await p.keyboard.press('Tab');assert(await dialog.evaluate(e=>e.contains(document.activeElement)));
   await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});await p.waitForFunction(()=>document.documentElement.style.overflow==='');
   assert.equal(await p.locator('html').evaluate(e=>e.style.overflow),'');
   assert(await p.getByRole('button',{name:'放大新闻图片',exact:true}).evaluate(e=>document.activeElement===e));
   await p.getByRole('button',{name:'放大新闻图片',exact:true}).click();await dialog.waitFor();
   await p.getByRole('button',{name:'关闭大图'}).click();await dialog.waitFor({state:'hidden'});
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   if(i===1)await p.screenshot({path:'/qa/news-image-modes-'+width+'.png',fullPage:true});
   console.log('ADAPTIVE_IMAGE_OK',width,w+'x'+h);
  }
  await p.goto('http://cdnews.test/news/article/4');await p.getByText('图片暂不可用',{exact:true}).waitFor();assert.equal(await p.locator('.article-cover-tools').count(),0);
  await p.goto('http://cdnews.test/news/article/5');await p.locator('.paragraphs').waitFor();assert.equal(await p.locator('.article-cover').count(),0);
  assert.deepEqual(errors,[]);console.log('FALLBACK_AND_NO_COVER_OK',width);await c.close();
 }
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
