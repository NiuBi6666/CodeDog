<script setup>
import { ref,nextTick,watch,onMounted,onBeforeUnmount } from 'vue';
const props=defineProps({articleId:{type:Number,required:true}});
const panel=ref(null),text=ref(''),translation=ref(''),provider=ref(''),state=ref('idle'),error=ref(''),position=ref({left:'8px',top:'8px'});
let anchor,controller,ticket=0,debounce,timer;
function close(clear=false){
 ticket++;controller?.abort();clearTimeout(timer);text.value='';translation.value='';error.value='';state.value='idle';
 if(clear)window.getSelection()?.removeAllRanges();
}
async function place(){
 await nextTick();if(!panel.value||!anchor)return;
 const width=panel.value.offsetWidth,height=panel.value.offsetHeight;
 let top=anchor.bottom+8;
 if(top+height>innerHeight-8)top=anchor.top-height-8;
 position.value={left:Math.max(8,Math.min(innerWidth-width-8,anchor.left))+'px',top:Math.max(8,Math.min(innerHeight-height-8,top))+'px'};
}
function readSelection(){
 const selection=window.getSelection();
 if(!selection||selection.isCollapsed||!selection.rangeCount){if(state.value==='idle')close();return;}
 const node=selection.anchorNode;
 if(panel.value?.contains(node))return;
 const range=selection.getRangeAt(0),common=range.commonAncestorContainer;
 const element=common.nodeType===Node.ELEMENT_NODE?common:common.parentElement;
 if(!element?.closest('[data-selectable-original]')){close();return;}
 const value=selection.toString().replace(/\s+/g,' ').trim();
 if(!/[a-z]/i.test(value)){close();return;}
 if(value===text.value)return;
 close();text.value=value;anchor=range.getBoundingClientRect();
 if(value.length>1000){state.value='error';error.value='每次最多翻译 1000 个字符，请缩小选区。';}
 place();
}
function selectionChanged(){clearTimeout(debounce);debounce=setTimeout(readSelection,160);}
function pointerUp(event){if(!panel.value?.contains(event.target))selectionChanged();}
function pointerDown(event){if(text.value&&!panel.value?.contains(event.target))close();}
function keyDown(event){if(event.key==='Escape'&&text.value){close(true);}}
function scrolled(event){if(text.value&&!panel.value?.contains(event.target))close();}
async function translate(){
 if(!text.value||text.value.length>1000||state.value==='loading')return;
 controller?.abort();controller=new AbortController();
 const current=++ticket,signal=controller.signal;
 state.value='loading';error.value='';place();
 clearTimeout(timer);timer=setTimeout(()=>controller.abort(),40000);
 try{
  const csrfResponse=await fetch('/api/auth/csrf',{signal,credentials:'same-origin',cache:'no-store'});
  if(!csrfResponse.ok)throw Error('无法获取翻译凭证，请重试');
  const csrf=await csrfResponse.json();
  const response=await fetch('/api/news/translate',{method:'POST',signal,credentials:'same-origin',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':csrf.token},body:JSON.stringify({article_id:props.articleId,text:text.value})});
  let body;try{body=await response.json();}catch(_){throw Error('翻译服务暂不可用，请稍后重试');}
  if(!response.ok)throw Error(body.error||(response.status===429?'翻译过于频繁，请稍后再试':'翻译暂未完成，请重试'));
  if(!body.translation)throw Error('没有收到译文，请重试');
  if(current!==ticket)return;
  translation.value=body.translation;provider.value=body.provider;state.value='done';
 }catch(e){
  if(current!==ticket)return;
  state.value='error';error.value=e.name==='AbortError'?'翻译等待超时，请稍后重试。':e.message;
 }finally{if(current===ticket){clearTimeout(timer);place();}}
}
watch(()=>props.articleId,()=>close());
onMounted(()=>{
 document.addEventListener('selectionchange',selectionChanged);
 document.addEventListener('pointerup',pointerUp);
 document.addEventListener('pointerdown',pointerDown);
 document.addEventListener('keydown',keyDown);
 document.addEventListener('scroll',scrolled,true);
 window.addEventListener('resize',close);
});
onBeforeUnmount(()=>{
 close();clearTimeout(debounce);
 document.removeEventListener('selectionchange',selectionChanged);
 document.removeEventListener('pointerup',pointerUp);
 document.removeEventListener('pointerdown',pointerDown);
 document.removeEventListener('keydown',keyDown);
 document.removeEventListener('scroll',scrolled,true);
 window.removeEventListener('resize',close);
});
</script>
<template>
 <Teleport to="body">
  <section v-if="text" ref="panel" class="selection-translator" :style="position" role="dialog" aria-label="划词翻译" :aria-busy="state==='loading'">
   <header><strong>划词翻译</strong><button type="button" class="selection-close" aria-label="关闭划词翻译" @pointerdown.prevent @click="close(true)">×</button></header>
   <p class="selection-original" lang="en">{{text}}</p>
   <button v-if="state==='idle'" class="selection-translate" type="button" @pointerdown.prevent @click="translate">翻译选中内容 →</button>
   <div aria-live="polite" aria-atomic="true">
    <p v-if="state==='loading'" class="selection-status" role="status">正在翻译…</p>
    <template v-if="state==='done'"><p class="selection-result" lang="zh-CN">{{translation}}</p><small class="selection-provider">{{provider}}</small></template>
    <p v-if="state==='error'" class="selection-error" role="alert">{{error}}</p>
   </div>
   <button v-if="state==='error'&&text.length<=1000" class="selection-translate" type="button" @pointerdown.prevent @click="translate">重新翻译</button>
  </section>
 </Teleport>
</template>
