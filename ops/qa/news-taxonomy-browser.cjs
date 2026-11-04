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
 const realFetch=window.fetch.bind(window);window.taxonomyRequests=[];
 const zh={id:91002,source_name:'UN News 中文',category:'生活',sub_tag:'医疗健康',lang:'zh',title_en:'中文医疗原稿',title_original:'中文医疗原稿',title_zh:'中文医疗原稿',content_en:['这是中文原稿第一段。','这是中文原稿第二段。'],content_zh:['这是中文原稿第一段。','这是中文原稿第二段。'],translation_status:'ready',origin_url:'https://example.com/chinese',published_at:'2026-11-04T01:00:00Z',summary_zh:''};
 const en={id:91001,source_name:'TechCrunch',category:'科技',sub_tag:'AI与创投',lang:'en',title_en:'A research team releases a learning agent',title_original:'A research team releases a learning agent',title_zh:'研究团队发布学习智能体',content_en:['The first original paragraph.','The second original paragraph.'],content_zh:['第一段译文。','第二段译文。'],translation_status:'ready',origin_url:'https://example.com/english',published_at:'2026-11-04T01:00:00Z',summary_zh:''};
 const rows=[en,{...en,id:91003,source_name:'Ars Technica',sub_tag:'硬核科技',title_en:'Semiconductor production expands with new chip factories',title_original:'Semiconductor production expands with new chip factories',title_zh:'芯片工厂扩大产能'},zh];
 window.fetch=async(url,options={})=>{
  const parsed=new URL(url,location.origin);
  if(!parsed.pathname.startsWith('/api/news'))return realFetch(url,options);
  window.taxonomyRequests.push({url:String(url),method:options.method||'GET'});
  if(options.method&&options.method!=='GET')throw Error('Unexpected translation or collection request');
  let data;
  if(parsed.pathname==='/api/news'){
   const c=parsed.searchParams.get('category')||'',tag=parsed.searchParams.get('sub_tag')||'';
   const list=rows.filter(a=>(!c||a.category===c)&&(!tag||a.sub_tag===tag));
   data={articles:list,total:list.length,sources:rows.map(a=>({source_name:a.source_name})),sub_tags:[...new Set(rows.filter(a=>!c||a.category===c).map(a=>a.sub_tag))]};
  }else data=rows.find(a=>a.id===Number(parsed.pathname.split('/').pop()));
  return new Response(JSON.stringify(data),{status:data?200:404,headers:{'Content-Type':'application/json'}});
 };
})()`});
 const origin=process.env.QA_ORIGIN||'http://127.0.0.1:8098';
 const expected=['全部','国际','军事','科技','财经','教育','文娱','生活'];
 for(const width of [320,375,390,430,768,1024,1440]){
  await send('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false});
  await send('Page.navigate',{url:origin+'/news'});await until("document.querySelector('.story-card')");
  const layout=await evaluate("({labels:[...document.querySelectorAll('.category-nav button')].map(e=>e.textContent),overflow:document.documentElement.scrollWidth>innerWidth})");
  if(layout.overflow||JSON.stringify(layout.labels)!==JSON.stringify(expected))throw Error('Invalid taxonomy layout at '+width);
  console.log('PASS seven primary categories, no overflow at '+width);
 }
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:900,deviceScaleFactor:1,mobile:false});
 await evaluate("[...document.querySelectorAll('.category-nav button')].find(e=>e.textContent==='科技').click()");
 await until("document.querySelector('.subtag-nav')");
 await evaluate("[...document.querySelectorAll('.subtag-nav button')].find(e=>e.textContent==='AI与创投').click()");
 await until("document.querySelectorAll('.story-card').length===1");
 if(!await evaluate("document.querySelector('.category-tag').textContent==='AI与创投'"))throw Error('Subtag filtering failed');
 await evaluate("window.taxonomyRequests=[];document.querySelector('.sync-button').click()");
 await until("!document.querySelector('.sync-button').disabled");
 if(!await evaluate("window.taxonomyRequests.length===1&&window.taxonomyRequests[0].method==='GET'&&new URL(window.taxonomyRequests[0].url,location.origin).searchParams.get('sub_tag')==='AI与创投'"))throw Error('Refresh did not retain subtag or dispatched work');
 console.log('PASS subtag filter and read-only refresh');
 await evaluate("[...document.querySelectorAll('.category-nav button')].find(e=>e.textContent==='生活').click()");
 await until("document.querySelector('.category-tag')?.textContent==='医疗健康'");
 await evaluate("window.taxonomyRequests=[];document.querySelector('.story-card').click()");
 await until("document.querySelector('.original-language-note')");
 await evaluate("document.querySelector('.global-modes button:last-child').click()");
 if(!await evaluate("document.querySelectorAll('.en-paragraph').length===0&&document.querySelectorAll('.zh-paragraph').length===2&&window.taxonomyRequests.every(r=>r.method==='GET')"))throw Error('Chinese original was duplicated, mislabeled or sent for translation');
 await snapshot('news-taxonomy-chinese-390');
 console.log('PASS Chinese original displays once in every mode and never requests translation');
 await evaluate("[...document.querySelectorAll('.category-nav button')].find(e=>e.textContent==='科技').click()");
 await until("document.querySelectorAll('.story-card').length===2 && document.querySelector('.category-tag')?.textContent==='AI与创投'");
 await evaluate("document.querySelector('.global-modes button:nth-child(2)').click();document.querySelector('.story-card').click()");
 await wait(1500);
 await until("document.querySelectorAll('.en-paragraph').length===2");
 if(!await evaluate("document.querySelector('.paragraph-pair').firstElementChild.classList.contains('en-paragraph')&&document.querySelectorAll('.zh-paragraph').length===2"))throw Error('English first bilingual alignment regressed');
 console.log('PASS English paragraph stays above its Chinese translation');
 await send('Browser.close').catch(()=>{});
}
main().then(()=>process.exit(0)).catch(e=>{console.error(e.message);if(socket)socket.close();process.exit(1)});
