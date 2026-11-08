import {leadExcerpt} from './newspaper-layout.js';
const cache=new Map(),pending=new Map(),queue=[];
let active=0;
function pump(){
 while(active<2&&queue.length){
  const job=queue.shift();active++;
  job().finally(()=>{active--;pump();});
 }
}
export function loadStoryExcerpt(article){
 if(article.content_original||article.content_en)return Promise.resolve(leadExcerpt(article));
 const id=article.id;
 if(cache.has(id))return Promise.resolve(cache.get(id));
 if(pending.has(id))return pending.get(id);
 const task=new Promise((resolve,reject)=>{
  queue.push(async()=>{
   const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
   try{
    const response=await fetch('/api/news/'+encodeURIComponent(id),{signal:controller.signal});
    if(!response.ok)throw Error('导语暂不可用');
    const pairs=leadExcerpt(await response.json());
    if(cache.size>=80)cache.delete(cache.keys().next().value);
    cache.set(id,pairs);resolve(pairs);
   }catch(error){reject(error);}finally{clearTimeout(timer);pending.delete(id);}
  });
 });
 pending.set(id,task);pump();return task;
}
export function clipExcerpt(text,limit=100){
 const chars=Array.from((text||'').replace(/\s+/g,' ').trim());
 if(chars.length<=limit)return chars.join('');
 let shortened=chars.slice(0,limit).join('');
 if(/[A-Za-z]$/.test(shortened)&&/[A-Za-z]/.test(chars[limit]))shortened=shortened.replace(/\s+\S*$/,'');
 return shortened.replace(/[，、；：,\s]+$/,'')+'…';
}
