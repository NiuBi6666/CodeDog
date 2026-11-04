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
 await send('Page.navigate',{url:origin+'/news'});
 await until("document.querySelector('.story-card')");
 await evaluate(`(()=>{
 const original=window.fetch.bind(window);
 window.testRequests=[];window.testMode='delay';
 window.fetch=async (url,options={})=>{
  window.testRequests.push({url:String(url),method:options.method||'GET'});
  if(String(url).startsWith('/api/news?')){
   if(window.testMode==='delay')await new Promise(resolve=>window.releaseNews=resolve);
   if(window.testMode==='failure')return new Response(JSON.stringify({error:'测试网络错误'}),{status:503,headers:{'Content-Type':'application/json'}});
   const response=await original(url,options);
   if(window.testMode==='new'){
    const data=await response.json();data.articles.unshift({...data.articles[0],id:999999,title_en:'Fresh original report',title_zh:'测试新增报道'});data.total++;
    return new Response(JSON.stringify(data),{status:200,headers:{'Content-Type':'application/json'}});
   }
   return response;
  }
  return original(url,options);
 };
})()`);
 await evaluate("document.querySelector('.sync-button').click();document.querySelector('.sync-button').click()");
 await wait(100);
 if(!await evaluate("document.querySelector('.sync-button').disabled && document.querySelector('.story-card') && window.testRequests.length===1"))throw Error('Refresh must retain stories and prevent duplicate requests');
 await evaluate("window.releaseNews()");
 await until("!document.querySelector('.sync-button').disabled");
 await wait(5200);
 if(!await evaluate("window.testRequests.length===1 && window.testRequests[0].method==='GET' && window.testRequests[0].url.startsWith('/api/news?') && document.querySelector('.notice').textContent.includes('列表已更新')"))throw Error('Refresh triggered collection or polling');
 console.log('PASS delayed read retains list, immediate feedback, duplicate guard, no dispatch or polling');
 await evaluate("window.testMode='failure';document.querySelector('.sync-button').click()");
 await until("!document.querySelector('.sync-button').disabled");
 if(!await evaluate("document.querySelector('.notice').textContent.includes('测试网络错误') && document.querySelector('.story-card')"))throw Error('Failed read claimed success or removed previous stories');
 console.log('PASS read failure retains previous stories and reports error');
 await evaluate("window.testMode='new';document.querySelector('.sync-button').click()");
 await until("!document.querySelector('.sync-button').disabled");
 if(!await evaluate("document.querySelector('.notice').textContent.includes('1 篇新报道')"))throw Error('New report count wrong');
 console.log('PASS new report insertion and count');
 await evaluate("window.testMode='real';const input=document.querySelector('.search input');input.value='Cramer';input.dispatchEvent(new Event('input',{bubbles:true}))");
 await wait(900);
 await evaluate("window.testRequests=[];document.querySelector('.sync-button').click()");
 await until("!document.querySelector('.sync-button').disabled");
 if(!await evaluate("window.testRequests.length===1 && new URL(window.testRequests[0].url,location.origin).searchParams.get('q')==='Cramer' && new URL(window.testRequests[0].url,location.origin).searchParams.get('page')==='0'"))throw Error('Refresh lost filters or page reset');
 console.log('PASS read-only refresh retains search and resets pagination');
 await send('Browser.close').catch(()=>{});
}
main().then(()=>process.exit(0)).catch(e=>{console.error(e.message);if(socket)socket.close();process.exit(1)});
