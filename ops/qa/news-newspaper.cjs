const {chromium}=require('/qa/node_modules/playwright'),assert=require('node:assert/strict'),path=require('path');
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9223');
 try{for(const width of [375,390,430,768,1024,1440]){
  const context=await browser.newContext({viewport:{width,height:1000}}),p=await context.newPage(),errors=[],posts=[];
  const rows=Array.from({length:24},(_,i)=>({id:i+1,title_en:['A new chapter for cities and the people who shape them','Scientists trace the changing rhythms of our planet','Markets respond to a new generation of clean energy'][i%3],title_zh:['城市与居民共同书写绿色转型新篇章','科学家追踪地球生态节律的变化','新一代清洁能源引发全球市场关注'][i%3]+' · '+(i+1),source_name:['The Guardian','BBC News','Reuters'][i%3],category:['国际','科技','财经'][i%3],lang:'en',published_at:'2026-11-08T08:00:00Z',cover_image:i%3===0?'https://image.test/landscape.svg':'',origin_url:'https://example.com/story/'+i,translation_status:'ready',content_en:'The world is changing around us, and local communities are finding new ways to respond.\n\nResearchers are working across borders to understand the challenges facing our cities.\n\nThe findings offer a new perspective on the future.',content_zh:'世界正在发生变化，当地社区正在寻找新的应对方式。\n\n研究人员正跨越国界，研究城市面临的挑战。\n\n这些发现为未来提供了全新的视角。'}));
  p.on('pageerror',e=>errors.push(e.message));
  let dimensions=[1600,900];
  await context.route('https://wsrv.nl/**',r=>r.fulfill({contentType:'image/svg+xml',body:dimensions?'<svg xmlns="http://www.w3.org/2000/svg" width="'+dimensions[0]+'" height="'+dimensions[1]+'"><rect width="100%" height="100%" fill="#9faaa6"/></svg>':'invalid-image'}));
  await context.route('http://cdnews.test/**',async r=>{
   const u=new URL(r.request().url());let data;
   if(u.pathname==='/api/news'){
    let filtered=rows.filter(a=>!u.searchParams.get('category')||a.category===u.searchParams.get('category'));
    if(u.searchParams.get('q')==='empty')filtered=[];
    data={articles:filtered.map(({content_en,content_zh,...card})=>card),total:filtered.length,has_more:false,sources:[]};
   }else if(/^\/api\/news\/\d+$/.test(u.pathname))data=rows.find(a=>a.id===Number(u.pathname.split('/').pop()));
   else if(u.pathname==='/api/auth/csrf')data={token:'test'};
   else if(u.pathname==='/api/news/translate'){posts.push(u.pathname);data={translation:'世界',cached:true};}
   else if(u.pathname.includes('/translation')){posts.push(u.pathname);data={};}
   if(data)return r.fulfill({json:data});
   const rel=u.pathname.replace(/^\/news\/?/,'');const f=rel.startsWith('assets/')?path.join('/site',rel):'/site/index.html';
   return r.fulfill({path:f,contentType:f.endsWith('.js')?'application/javascript':f.endsWith('.css')?'text/css':'text/html'});
  });
  await p.goto('http://cdnews.test/newspaper');await p.locator('.press-lead h2').waitFor();
  await p.waitForFunction(()=>document.querySelector('.press-lead-excerpt'));
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'no horizontal overflow');
  assert.equal(posts.length,0,'homepage must not enqueue body translation');
  assert.equal(await p.locator('.press-news-grid .press-story').count(),15);
  await p.screenshot({path:'/qa/newspaper-home-'+width+'.png',fullPage:false});
  const textCard=p.locator('.press-news-grid .press-story-text').first();await textCard.scrollIntoViewIfNeeded();
  await textCard.locator('.press-card-excerpt p[lang="zh-CN"]').waitFor();
  assert.equal(await textCard.locator('.press-thumb').count(),0);
  assert((await textCard.locator('.press-card-excerpt p[lang="zh-CN"]').textContent()).length<=101);
  assert.equal(await textCard.locator('.press-card-excerpt p').first().getAttribute('lang'),'en');
  assert.equal(await textCard.locator('.press-card-excerpt').evaluate(e=>getComputedStyle(e).textAlign),'justify');
  const imageCard=p.locator('.press-news-grid .press-story').filter({has:p.locator('.press-thumb')}).first();
  assert.equal(await imageCard.locator('.press-card-excerpt').count(),0);
  assert.equal(posts.length,0,'card excerpts must not enqueue translation');
  await p.screenshot({path:'/qa/newspaper-text-cards-'+width+'.png',fullPage:false});
  const link=p.locator('.press-news-grid .press-story a').nth(4);await link.scrollIntoViewIfNeeded();const before=await p.evaluate(()=>scrollY);
  await link.click();await p.locator('.press-dialog[open] .press-article').waitFor();
  assert(new URL(p.url()).searchParams.has('article'));
  assert.equal(await p.locator('.press-dialog .press-paragraph').count(),3);
  assert.equal(await p.locator('.press-dialog .press-paragraph').first().locator('p').first().getAttribute('lang'),'en');
  await p.screenshot({path:'/qa/newspaper-article-'+width+'.png',fullPage:false});
  const en=p.locator('.press-dialog [data-selectable-original]').first();
  await en.evaluate(e=>{const range=document.createRange();range.selectNodeContents(e);const s=window.getSelection();s.removeAllRanges();s.addRange(range);document.dispatchEvent(new Event('selectionchange'));});
  await p.getByRole('dialog',{name:'划词翻译'}).getByText('世界',{exact:true}).waitFor();
  assert(await p.locator('.press-dialog .selection-translator').count());
  await p.getByRole('button',{name:'关闭划词翻译'}).click();
  await p.getByRole('button',{name:'关闭报道',exact:true}).click();
  await p.waitForFunction(()=>!document.querySelector('.press-dialog').open);
  assert(Math.abs(await p.evaluate(()=>scrollY)-before)<3,'return to same location');
  assert.equal(await p.evaluate(()=>document.documentElement.style.overflow),'');
  await p.evaluate(()=>scrollTo(0,0));await p.getByRole('button',{name:'科技',exact:true}).click();
  await p.waitForFunction(()=>document.querySelector('.press-section-label')?.textContent.includes('科技'));
  assert(await p.locator('.press-byline').first().textContent().then(v=>v.includes('科技')));
  await p.getByRole('textbox',{name:'搜索报道'}).fill('empty');await p.getByText('暂无报道',{exact:true}).waitFor();
  await p.getByRole('textbox',{name:'搜索报道'}).fill('');await p.locator('.press-lead h2').waitFor();
  await p.locator('.press-lead-link').click();await p.locator('.press-dialog[open] .press-article').waitFor();await p.keyboard.press('Escape');await p.waitForFunction(()=>!document.querySelector('.press-dialog').open);
  await p.goto('http://cdnews.test/newspaper?article=1');await p.locator('.press-dialog[open] .press-article').waitFor();
  await p.getByRole('button',{name:'关闭报道',exact:true}).click();await p.waitForFunction(()=>!document.querySelector('.press-dialog').open);

  if(width===390){
   let enqueued=false,failList=false;
   await context.route('http://cdnews.test/api/news**',async r=>{
    const u=new URL(r.request().url());
    if(u.pathname==='/api/news'){
     if(failList)return r.fulfill({status:503,json:{error:'暂时无法读取报道'}});
     const next=u.searchParams.has('cursor');return r.fulfill({json:{articles:next?rows.slice(12):rows.slice(0,12),total:24,has_more:!next,next_cursor:next?'':'page2'}});
    }
    if(u.pathname==='/api/news/2')return r.fulfill({json:{...rows[1],translation_status:enqueued?'ready':'pending',content_zh:enqueued?rows[1].content_zh:''}});
    if(u.pathname==='/api/news/2/translation'){enqueued=true;return r.fulfill({json:{status:'queued'}});}
    return r.fallback();
   });
   await p.goto('http://cdnews.test/newspaper');await p.locator('.press-lead h2').waitFor();
   await p.evaluate(()=>scrollTo(0,document.body.scrollHeight));
   await p.waitForFunction(()=>document.querySelectorAll('.press-news-grid .press-story').length===15);
   await p.goto('http://cdnews.test/newspaper?article=2');await p.locator('.press-dialog[open] .press-article').waitFor();
   await p.waitForFunction(()=>document.querySelector('.press-dialog p[lang="zh-CN"]'));
   assert(enqueued,'detail must request translation when needed');
   await p.getByRole('button',{name:'关闭报道',exact:true}).click();await p.waitForFunction(()=>!document.querySelector('.press-dialog').open);
   failList=true;await p.reload();await p.getByRole('alert').getByText('暂时无法读取报道',{exact:false}).waitFor();
   failList=false;await p.getByRole('button',{name:'重试',exact:true}).click();await p.locator('.press-lead h2').waitFor();
   console.log('NEWSPAPER_PAGINATION_TRANSLATION_ERROR_RECOVERY_OK');
  }

  await p.getByRole('button',{name:'全部',exact:true}).click();
  for(const shape of [[600,1000],[900,900],[1600,900]]){
   dimensions=shape;
   await p.goto('http://cdnews.test/newspaper');await p.locator('.press-lead-excerpt').waitFor();
   await p.waitForFunction(()=>document.querySelector('.press-lead-photo img')?.naturalWidth>0);
   const image=await p.locator('.press-lead-photo img').boundingBox(),body=await p.locator('.press-lead-body').boundingBox(),figure=await p.locator('.press-lead-figure').boundingBox();
   assert(Math.abs(image.width/image.height-shape[0]/shape[1])<.02,'natural image ratio must be preserved');
   if(width>=768){
    assert(Math.abs(figure.width/body.width-.48)<.01,'photo occupies 48% of lead width');
    const text=await p.locator('.press-lead-excerpt p').first().evaluate(e=>{const r=document.createRange();r.setStart(e.firstChild,0);r.setEnd(e.firstChild,1);return r.getBoundingClientRect().x;});
    assert(text>=figure.x+figure.width+15,'real excerpt must fill the space to the right');
   }else assert(Math.abs(figure.width-body.width)<1,'mobile uses a full-width image');
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   await p.screenshot({path:'/qa/newspaper-wrap-'+width+'-'+shape.join('x')+'.png',fullPage:false});
  }
  dimensions=null;await p.reload();await p.getByText('The Guardian · 图片暂不可用',{exact:true}).waitFor();
  dimensions=[600,1000];
  rows[0].content_en=rows[0].title_en+'\n\n- Published\n\n'+rows[0].content_en;
  rows[0].content_zh=rows[0].title_zh+'\n\n- 已发布\n\n'+rows[0].content_zh;
  await p.reload();await p.locator('.press-lead-excerpt').waitFor();
  assert(!(await p.locator('.press-lead-excerpt').textContent()).includes('Published'));
  assert(!(await p.locator('.press-lead-excerpt').textContent()).includes(rows[0].title_en));
  assert((await p.locator('.press-lead-excerpt').textContent()).includes('The world is changing'));
  console.log('NEWSPAPER_NATURAL_IMAGES_WRAP_AND_CLEAN_EXCERPT_OK',width);
  assert.deepEqual(errors,[]);console.log('NEWSPAPER_LAYOUT_MODAL_SELECTION_FILTERS_OK',width);await context.close();
 }}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});