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
  throw Error('Page did not become ready: '+expression);
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
 let targets;
 for(let i=0;i<40;i++){try{targets=await(await fetch('http://127.0.0.1:9225/json/list')).json();break;}catch(e){await wait(250);}}
 socket=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
 await new Promise((r,j)=>{socket.addEventListener('open',r,{once:true});socket.addEventListener('error',j,{once:true});});
 socket.addEventListener('message',e=>{const m=JSON.parse(e.data);const p=pending.get(m.id);if(p){pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result);}});
 await send('Page.enable');await send('Network.enable');
 await send('Page.addScriptToEvaluateOnNewDocument',{source: String.raw`(()=>{
 const real=fetch.bind(window);
 window.calls=[];window.delayed=false;window.fail=false;
 const a={id:999,source_name:'News QA',lang:'en',category:'科技',title_en:'Original headline',title_zh:'中文标题',content_en:'First English sentence. Second English sentence. Third English sentence.',content_zh:'中文正文。',published_at:'2026-11-05T08:00:00Z',translation_status:'ready'};
 window.fetch=async(url,opts={})=>{
 const path=new URL(url,location.origin).pathname;
 let data;
 if(path==='/api/news')data={articles:[a],sources:[],total:1,has_more:false};
 else if(path==='/api/auth/csrf')data={token:'fixture'};
 else if(path==='/api/news/999')data=a;
 else if(path==='/api/news/translate'){
  const input=JSON.parse(opts.body);window.calls.push(input.text);
  if(window.fail){window.fail=false;return new Response('{"error":"模拟失败"}',{status:503});}
  if(window.delayed){window.delayed=false;await new Promise(r=>window.releaseTranslation=r);}
  data={translation:'译文：'+input.text,provider:'mtran-bing'};
 }else return real(url,opts);
 return new Response(JSON.stringify(data),{headers:{'Content-Type':'application/json'}});
 };
 window.selectText=(start,end,selector='.en-paragraph')=>{
 const node=document.querySelector(selector).firstChild;
 const range=document.createRange();range.setStart(node,start);range.setEnd(node,end);
 const selection=getSelection();selection.removeAllRanges();selection.addRange(range);
 document.dispatchEvent(new Event('selectionchange'));
 };
 })()`});

 for(const width of [375,390,430,1440]){
 await send('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false});
 await send('Page.navigate',{url:(process.env.QA_ORIGIN||'http://127.0.0.1:8098')+'/news/article/999'});
 await until("document.querySelector('.headline-en')");
 const result=await evaluate("(()=>{const e=document.querySelector('.headline-en'),z=document.querySelector('.reader-content>h1'),se=document.querySelector('.story-en'),sz=document.querySelector('.story-card h2');return {order:e.nextElementSibling===z&&se.nextElementSibling===sz,enSize:parseFloat(getComputedStyle(e).fontSize),zhSize:parseFloat(getComputedStyle(z).fontSize),weight:getComputedStyle(z).fontWeight,color:getComputedStyle(z).color,overflow:document.documentElement.scrollWidth>innerWidth}})()");
 if(!result.order||result.enSize>16||result.zhSize<24||Number(result.weight)<700||result.overflow)throw Error('Bilingual headline hierarchy failed '+JSON.stringify(result));
 await evaluate("window.selectText(0,8,'.headline-en')");
 await until("document.querySelector('.selection-result')");
 await evaluate("document.querySelector('.selection-close').click();document.querySelectorAll('.global-modes button')[0].click()");
 await until("!document.querySelector('.headline-en')&&!document.querySelector('.story-en')");
 if(!await evaluate("document.querySelector('.reader-content>h1').textContent==='中文标题'"))throw Error('Chinese-only heading');
 await evaluate("document.querySelectorAll('.global-modes button')[2].click()");
 await until("document.querySelector('.reader-content>h1').textContent==='Original headline'");
 if(!await evaluate("!document.querySelector('.headline-en')&&!document.querySelector('.story-en')"))throw Error('Duplicate English heading');
 await evaluate("document.querySelectorAll('.global-modes button')[1].click()");
 await until("document.querySelector('.headline-en')");
 await snapshot('headline-order-'+width);
 console.log('PASS '+width+' English-first titles, Chinese visual hierarchy, language modes, selection translation and no overflow');
 }
 await send('Browser.close').catch(()=>{});
}
main().then(()=>process.exit(0)).catch(e=>{console.error(e.message);if(socket)socket.close();process.exit(1)});
