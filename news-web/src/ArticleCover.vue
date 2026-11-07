<script setup>
import { ref, watch, onBeforeUnmount } from 'vue';
const props=defineProps({src:{type:String,required:true},alt:{type:String,default:''},sourceMark:{type:String,default:'C'}});
const modes=[{id:'contain',label:'完整显示'},{id:'compact',label:'紧凑裁切'},{id:'natural',label:'原始比例'}];
const preferenceKey='cdnews.cover-mode';
function preference(){try{const v=localStorage.getItem(preferenceKey);return modes.some(m=>m.id===v)?v:'contain';}catch{return 'contain';}}
const mode=ref(preference()),failed=ref(false),dialog=ref(null),trigger=ref(null),closeButton=ref(null);
let previousOverflow=null;
function changeMode(value){mode.value=value;try{localStorage.setItem(preferenceKey,value);}catch{}}
function imageUrl(){return 'https://wsrv.nl/?url='+encodeURIComponent(props.src);}
function enlarge(){if(failed.value||!dialog.value||dialog.value.open)return;dialog.value.showModal();previousOverflow=document.documentElement.style.overflow;document.documentElement.style.overflow='hidden';}
function restoreScroll(){if(previousOverflow!==null){document.documentElement.style.overflow=previousOverflow;previousOverflow=null;}}
function close(){dialog.value?.close();restoreScroll();}
function closed(){restoreScroll();trigger.value?.focus({preventScroll:true});}
watch(()=>props.src,()=>{close();failed.value=false;});
onBeforeUnmount(()=>{close();});
</script>
<template>
 <figure class="article-cover" :data-image-mode="mode">
  <button v-if="!failed" ref="trigger" type="button" class="article-cover-stage" aria-label="放大新闻图片" @click="enlarge">
   <img :src="imageUrl()" :alt="alt" decoding="async" @error="failed=true" />
  </button>
  <div v-else class="article-cover-unavailable" role="status"><span>{{sourceMark}}</span><small>图片暂不可用</small></div>
  <div v-if="!failed" class="article-cover-tools">
   <div class="article-cover-modes" role="group" aria-label="图片显示方式"><button v-for="item in modes" :key="item.id" type="button" :aria-pressed="mode===item.id" @click="changeMode(item.id)">{{item.label}}</button></div>
   <button type="button" class="article-cover-expand" aria-label="全屏查看图片" @click="enlarge"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5M3 3l6 6m12-6-6 6M3 21l6-6m12 6-6-6"/></svg></button>
  </div>
  <dialog ref="dialog" class="article-cover-dialog" aria-label="新闻图片大图" @keydown.tab.prevent="closeButton?.focus()" @close="closed" @click="e=>{if(e.target===dialog)close();}">
   <button ref="closeButton" type="button" class="article-cover-close" aria-label="关闭大图" autofocus @click="close"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M6 18 18 6"/></svg></button>
   <img v-if="!failed" :src="imageUrl()" :alt="alt" decoding="async" />
  </dialog>
 </figure>
</template>
<style scoped>
.article-cover{margin:0 0 20px;width:100%;min-width:0}
.article-cover-stage{display:block;width:100%;height:clamp(180px,32dvh,360px);padding:0;border:1px solid var(--line);border-radius:8px;background:#f0efeb;overflow:hidden;cursor:zoom-in}
.article-cover-stage img{display:block;width:100%;height:100%;object-fit:contain}
.article-cover[data-image-mode="compact"] .article-cover-stage{height:clamp(140px,22dvh,220px)}
.article-cover[data-image-mode="compact"] .article-cover-stage img{object-fit:cover}
.article-cover[data-image-mode="natural"] .article-cover-stage{height:auto}
.article-cover[data-image-mode="natural"] .article-cover-stage img{height:auto;max-height:none;object-fit:contain}
.article-cover-tools{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:5px}
.article-cover-modes{display:flex;gap:2px;min-width:0}
.article-cover-tools button{display:inline-flex;align-items:center;justify-content:center;min-height:32px;border:0;border-radius:4px;background:transparent;color:#64748b;font-size:11px;padding:5px 9px;white-space:nowrap}
.article-cover-tools button[aria-pressed="true"]{background:#e8edec;color:#314e60;font-weight:600}
.article-cover-tools button:hover{color:#28333d;background:#e8edec}
.article-cover-expand{flex:0 0 32px}
.article-cover :is(button,dialog):focus-visible{outline:2px solid var(--blue);outline-offset:3px}
.article-cover-unavailable{display:grid;place-items:center;align-content:center;height:180px;gap:8px;background:#eeede7;border:1px solid var(--line);border-radius:8px;color:#64748b}
.article-cover-unavailable span{font:48px var(--serif)}
.article-cover-unavailable small{font-size:12px}
.article-cover-dialog{position:fixed;inset:0;max-width:none;max-height:none;width:100vw;height:100dvh;margin:0;padding:54px 20px 20px;border:0;background:#17212aee;overflow:auto;overscroll-behavior:contain;color:#fff}
.article-cover-dialog[open]{display:flex;align-items:center;justify-content:center}
.article-cover-dialog::backdrop{background:#17212aee}
.article-cover-dialog img{display:block;max-width:100%;max-height:calc(100dvh - 74px);width:auto;height:auto;object-fit:contain}
.article-cover-close{position:fixed;right:16px;top:12px;display:grid;place-items:center;width:40px;height:40px;border:1px solid #ffffff40;border-radius:50%;color:#fff;background:#26343f;z-index:1}
@media(max-width:760px){.article-cover-stage{height:clamp(180px,32dvh,300px)}.article-cover-tools button{min-height:40px;padding:6px 8px}.article-cover-expand{flex-basis:40px}.article-cover-dialog{padding:60px 10px 20px}.article-cover-dialog img{max-height:calc(100dvh - 80px)}}
</style>
