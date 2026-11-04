const fs = require('fs');
const root = '/qa';
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
let socket, serial = 0;
const pending = new Map();
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++serial;
  const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)); }, 20000);
  pending.set(id, {resolve: value => { clearTimeout(timer); resolve(value); }, reject});
  socket.send(JSON.stringify({id, method, params}));
});
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', {expression, awaitPromise: true, returnByValue: true});
  if (result.exceptionDetails) throw Error('Browser evaluation failed');
  return result.result?.value;
}
async function until(expression) {
  for (let i = 0; i < 80; i++) { if (await evaluate(expression)) return; await wait(250); }
  throw Error('Page did not become ready');
}
async function navigate(path, selector) {
  await send('Page.navigate', {url: 'https://codedog.online' + path});
  await until('Boolean(document.querySelector(' + JSON.stringify(selector) + '))');
  await wait(400);
}
async function snapshot(name) {
  const image = await send('Page.captureScreenshot', {format:'png', captureBeyondViewport:false});
  fs.writeFileSync(root + '/' + name + '.png', Buffer.from(image.data, 'base64'));
}


async function main(){
 let targets;for(let i=0;i<40;i++){try{targets=await(await fetch('http://127.0.0.1:9225/json/list')).json();break;}catch(e){if(i===39)throw e;await wait(250);}}
 socket=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',reject,{once:true});});
 socket.addEventListener('message',event=>{const m=JSON.parse(event.data);if(!m.id)return;const p=pending.get(m.id);if(!p)return;pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result);});
 await send('Page.enable');await send('Network.enable');await send('Network.setCacheDisabled',{cacheDisabled:true});

 await send('Page.addScriptToEvaluateOnNewDocument',{source:`(()=>{
 const realFetch=window.fetch.bind(window);
 window.feedRequests=[];window.feedFail=false;window.feedDelay=false;window.feedExtra=0;
 window.feedRows=Array.from({length:75},(_,i)=>({id:100-i,source_name:'原始新闻测试源',category:'科技',sub_tag:'AI与创投',lang:'zh',title_en:'第 '+i+' 条独立原稿报道，展示新闻阅读列表的正确先后顺序',title_zh:'第 '+i+' 条独立原稿报道，展示新闻阅读列表的正确先后顺序',published_at:new Date(Date.UTC(2026,10,4,12,0)-i*60000).toISOString(),summary_zh:''}));
 window.fetch=async(url,options={})=>{
  const p=new URL(url,location.origin);
  if(p.pathname!=='/api/news')return realFetch(url,options);
  window.feedRequests.push({url:String(url),method:options.method||'GET'});
  const cursor=p.searchParams.get('cursor');
  if(cursor&&window.feedDelay)await new Promise(resolve=>window.releaseFeed=resolve);
  if(cursor&&window.feedFail){window.feedFail=false;return new Response(JSON.stringify({error:'模拟加载失败'}),{status:503,headers:{'Content-Type':'application/json'}});}
  const category=p.searchParams.get('category');
  const data=category==='生活'?window.feedRows.slice(0,3).map(a=>({...a,category:'生活',sub_tag:'现代生活'})):window.feedRows;
  const index=cursor?data.findIndex(a=>a.id===Number(cursor))+1:0;
  const rows=data.slice(index,index+30),hasMore=index+rows.length<data.length;
  return new Response(JSON.stringify({articles:rows,total:data.length+window.feedExtra,sources:[{source_name:'原始新闻测试源'}],sub_tags:[category==='生活'?'现代生活':'AI与创投'],has_more:hasMore,next_cursor:hasMore?String(rows[rows.length-1].id):''}),{status:200,headers:{'Content-Type':'application/json'}});
 };
})()`});
 const origin=process.env.QA_ORIGIN||'http://127.0.0.1:8098';
 async function scrollBottom(mobile){await evaluate(mobile?"window.scrollTo(0,document.documentElement.scrollHeight)":"document.querySelector('.feed-panel').scrollTop=document.querySelector('.feed-panel').scrollHeight");}
 for(const width of [390,1440]){
  const mobile=width===390;
  await send('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false});
  await send('Page.navigate',{url:origin+'/news'});
  await until("document.querySelectorAll('.story-card').length===30");
  if(!await evaluate("!document.querySelector('.load-more')"))throw Error('Load-more button remains');
  await evaluate(mobile?"window.scrollTo(0,500)":"document.querySelector('.feed-panel').scrollTop=500");
  await evaluate("window.originalFirst=document.querySelector('.story-card');window.originalCount=document.querySelector('.count').textContent;window.feedExtra=7;window.savedScroll="+(mobile?'window.scrollY':"document.querySelector('.feed-panel').scrollTop")+";window.feedRequests=[];document.querySelector('.sync-button').click()");
  await until("!document.querySelector('.sync-button').disabled");
  if(!await evaluate("window.originalFirst===document.querySelector('.story-card') && window.originalCount===document.querySelector('.count').textContent && Math.abs(window.savedScroll-"+(mobile?'window.scrollY':"document.querySelector('.feed-panel').scrollTop")+")<1 && document.querySelector('.notice').textContent==='暂无更新的报道'"))throw Error('Unchanged head reset feed/count/scroll');
  console.log('PASS '+width+' no newer headline: DOM, count and scroll retained');
  await evaluate("window.feedRequests=[];window.feedDelay=true;window.feedFail=true");
  await scrollBottom(mobile);await until("window.releaseFeed");
  await scrollBottom(mobile);await wait(200);
  if(!await evaluate("window.feedRequests.length===1"))throw Error('Concurrent automatic loads');
  await evaluate("window.feedDelay=false;window.releaseFeed()");
  await until("document.querySelector('.feed-load-status button')");
  if(!await evaluate("document.querySelectorAll('.story-card').length===30"))throw Error('Failure erased previous stories');
  await evaluate("document.querySelector('.feed-load-status button').click()");
  await until("document.querySelectorAll('.story-card').length===60");
  if(!await evaluate("new URL(window.feedRequests[0].url,location.origin).searchParams.get('cursor')===new URL(window.feedRequests[1].url,location.origin).searchParams.get('cursor')"))throw Error('Retry skipped page');
  console.log('PASS '+width+' automatic load single request, failed-page retry keeps cursor');
  await scrollBottom(mobile);
  await until("document.querySelectorAll('.story-card').length===75");
  if(!await evaluate("document.querySelector('.feed-load-status').textContent.includes('已浏览全部报道')"))throw Error('No terminal state');
  const requests=await evaluate("window.feedRequests.length");await scrollBottom(mobile);await wait(400);
  if(await evaluate("window.feedRequests.length")!==requests)throw Error('Loading continued after end');
  console.log('PASS '+width+' scroll loads older stories in order and stops at end');
  await evaluate("window.feedRows.unshift({...window.feedRows[0],id:200,title_zh:'真正更新的头条',published_at:'2026-11-04T12:01:00Z'});document.querySelector('.sync-button').click()");
  await until("document.querySelector('.story-card h2')?.textContent==='真正更新的头条'");
  if(!await evaluate("document.querySelectorAll('.story-card').length===30 && document.querySelector('.notice').textContent==='已更新最新报道'"))throw Error('Newer head did not reset with fresh cursor');
  console.log('PASS '+width+' genuinely newer headline refreshes first page');
  await evaluate("window.feedDelay=true;window.releaseFeed=null");
  await scrollBottom(mobile);await until("window.releaseFeed");
  await evaluate("[...document.querySelectorAll('.category-nav button')].find(e=>e.textContent==='生活').click()");
  await until("document.querySelectorAll('.story-card').length===3");
  await evaluate("window.feedDelay=false;window.releaseFeed()");
  await wait(400);
  if(!await evaluate("document.querySelectorAll('.story-card').length===3 && [...document.querySelectorAll('.category-tag')].every(e=>e.textContent==='现代生活')"))throw Error('Stale append overwrote filter results');
  console.log('PASS '+width+' filtering discards stale append response');
 }
 await send('Browser.close').catch(()=>{});
}
main().then(()=>process.exit(0)).catch(e=>{console.error(e.message);if(socket)socket.close();process.exit(1)});
