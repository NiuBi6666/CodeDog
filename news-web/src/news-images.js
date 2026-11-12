export function isNewsImage(url){
 if(typeof url!=='string'||!url.startsWith('https://'))return false;
 try{
 const parsed=new URL(url),name=parsed.pathname.split('/').pop().toLowerCase();
 return !parsed.username&&!parsed.password&&!['lazyload-fallback.gif','lazyload-placeholder.gif','transparent.gif','spacer.gif','blank.gif','1x1.gif'].includes(name);
 }catch{return false;}
}

const cache=new Map();
let active=0;
const queue=[];
function run(task){return new Promise((resolve,reject)=>{queue.push(()=>{active++;task().then(resolve,reject).finally(()=>{active--;pump();});});pump();});}
function pump(){while(active<3&&queue.length)queue.shift()();}
export function newsImageUrl(url,original=false){
 if(typeof url!=='string'||!url.startsWith('https://'))return '';
 const parsed=new URL(url);
 if(parsed.hostname==='image.cnbcfm.com')return '/news/media/cnbc'+parsed.pathname+parsed.search;
 return '/news/media/'+(original?'original':'preview')+'?url='+encodeURIComponent(url);
}
function decodeImage(url){return new Promise((resolve,reject)=>{
 const img=new Image();let settled=false;
 const timer=setTimeout(()=>finish(new Error('timeout')),15000);
 function finish(error){if(settled)return;settled=true;clearTimeout(timer);img.onload=img.onerror=null;error?reject(error):resolve(url);}
 img.onload=()=>{(img.decode?img.decode():Promise.resolve()).then(()=>finish(),()=>finish(new Error('decode')));};
 img.onerror=()=>finish(new Error('image'));img.src=url;
});}
export function loadNewsImage(url,original=false){
 const key=(original?'original:':'preview:')+url;
 if(!cache.has(key)){
  const job=run(()=>decodeImage(newsImageUrl(url,original)).catch(()=>decodeImage('https://wsrv.nl/?url='+encodeURIComponent(url)+(original?'':'&w=1200&fit=inside&we&output=webp')))).catch(e=>{cache.delete(key);throw e;});
  cache.set(key,job);
  if(cache.size>80)cache.delete(cache.keys().next().value);
 }
 return cache.get(key);
}
let hoverTimer,hoverCount=0,hoverWindow=0;
export function prefetchNewsImage(url){
 clearTimeout(hoverTimer);
 if(!url||navigator.connection?.saveData||/2g/.test(navigator.connection?.effectiveType||''))return;
 if(Date.now()-hoverWindow>60000){hoverWindow=Date.now();hoverCount=0;}
 if(hoverCount>=8)return;
 hoverTimer=setTimeout(()=>{hoverCount++;loadNewsImage(url).catch(()=>{});},180);
}
