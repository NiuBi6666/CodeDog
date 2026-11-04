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

 const origin=process.env.QA_ORIGIN||'http://127.0.0.1:8098';
 const expected=['全部','科技','国际','财经','教育','科学','健康','环境','体育','文化','生活'];
 for(const width of [320,375,390,430,760,768,1024,1440]){
  await send('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false});
  await send('Page.navigate',{url:origin+'/news'});
  await until("document.querySelector('.story-card')");
  const layout=await evaluate(`(()=>{
   const nav=document.querySelector('.category-nav');
   return {labels:[...nav.querySelectorAll('button')].map(e=>e.textContent),overflow:document.documentElement.scrollWidth>innerWidth,scroll:nav.scrollWidth>nav.clientWidth,header:document.querySelector('.topbar').getBoundingClientRect().height};
  })()`);
  if(layout.overflow||JSON.stringify(layout.labels)!==JSON.stringify(expected))throw Error('Category layout invalid at '+width);
  if(width<=760&&((width<=430&&!layout.scroll)||layout.header!==143))throw Error('Mobile category sizing: '+JSON.stringify(layout));
  await evaluate("document.querySelector('.category-nav button:last-child').scrollIntoView({block:'nearest',inline:'nearest'});document.querySelector('.category-nav button:last-child').click()");
  await until("!document.querySelector('.feed-state')");
  if(!await evaluate("document.querySelector('.category-nav button:last-child').classList.contains('active') && document.documentElement.scrollWidth<=innerWidth"))throw Error('Last category not selectable');
  console.log('PASS '+width+'px: 10 categories, selectable last tab, no page overflow');
  if(width===390)await snapshot('news-categories-390');
 }
 await send('Browser.close').catch(()=>{});
}
main().then(()=>process.exit(0)).catch(e=>{console.error(e.message);if(socket)socket.close();process.exit(1)});
