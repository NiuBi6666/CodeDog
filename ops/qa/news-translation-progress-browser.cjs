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
 const targets=await(await fetch('http://127.0.0.1:9225/json/list')).json();
 socket=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
 await new Promise((r,j)=>{socket.addEventListener('open',r,{once:true});socket.addEventListener('error',j,{once:true});});
 socket.addEventListener('message',e=>{const m=JSON.parse(e.data);const p=pending.get(m.id);if(p){pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result);}});
 await send('Page.enable');await send('Network.enable');
 await send('Page.addScriptToEvaluateOnNewDocument',{source: String.raw`(()=>{
 const real=fetch.bind(window);window.stage=0;
 const a={id:999,source_name:'News QA',lang:'en',category:'科技',title_en:'Original headline',title_zh:'中文标题',content_en:'Original first.\n\nOriginal second.\n\nOriginal third.',published_at:'2026-11-04T08:00:00Z'};
 window.fetch=async(url,opts={})=>{
 const path=new URL(url,location.origin).pathname;
 let data;
 if(path==='/api/news')data={articles:[a],sources:[],total:1,has_more:false};
 else if(path==='/api/auth/csrf')data={token:'fixture'};
 else if(path==='/api/news/999/translation')data={translation_status:'processing'};
 else if(path==='/api/news/999'){
  if(window.stage===2)return new Response('{"error":"temporary"}',{status:503});
  const done=window.stage===3;
  data={...a,translation_status:done?'ready':'processing',translation_stage:done?'ready':'processing',paragraphs_zh:done?['第一段译文','第二段译文','第三段译文']:window.stage===1?['','第二段译文','']:['','',''],translation_completed:done?3:window.stage===1?1:0,translation_total:3};
 }else return real(url,opts);
 return new Response(JSON.stringify(data),{headers:{'Content-Type':'application/json'}});
 };
 })()`});
 for(const width of [390,1440]){
 await send('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:(process.env.QA_ORIGIN||'http://127.0.0.1:8098')+'/news/article/999'});
 await until("document.querySelectorAll('.paragraph-pair').length===3");
 await evaluate('window.stage=1');
 await until("document.querySelectorAll('.zh-paragraph').length===1");
 if(!await evaluate("!document.querySelectorAll('.paragraph-pair')[0].querySelector('.zh-paragraph')&&document.querySelectorAll('.paragraph-pair')[1].querySelector('.zh-paragraph').textContent==='第二段译文'"))throw Error('Paragraph alignment lost');
 if(!await evaluate("document.querySelector('.body-translation-status').textContent.includes('1/3')"))throw Error('Incorrect progress');
 await evaluate('window.stage=2');
 await until("document.querySelector('.body-translation-status').textContent.includes('连接恢复中')");
 await evaluate('window.stage=3');
 await until("document.querySelectorAll('.zh-paragraph').length===3&&!document.querySelector('.body-translation-status')");
 if(!await evaluate("[...document.querySelectorAll('.paragraph-pair')].every(p=>p.firstElementChild.classList.contains('en-paragraph'))"))throw Error('English order');
 if(!await evaluate("document.documentElement.scrollWidth<=window.innerWidth"))throw Error('Overflow');
 await snapshot('translation-progress-'+width);
 console.log('PASS '+width+' out-of-order progress, reconnect, completion and English-first layout');
 }
 await send('Browser.close').catch(()=>{});
}
main().then(()=>process.exit(0)).catch(e=>{console.error(e.message);if(socket)socket.close();process.exit(1)});
