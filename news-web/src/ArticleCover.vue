<script setup>
import { ref, watch, onBeforeUnmount } from 'vue';
const props=defineProps({src:{type:String,required:true},alt:{type:String,default:''},sourceMark:{type:String,default:'C'}});
const failed=ref(false),dialog=ref(null),trigger=ref(null),closeButton=ref(null);
let previousOverflow=null;
function imageUrl(){return 'https://wsrv.nl/?url='+encodeURIComponent(props.src);}
function enlarge(){if(failed.value||!dialog.value||dialog.value.open)return;dialog.value.showModal();previousOverflow=document.documentElement.style.overflow;document.documentElement.style.overflow='hidden';}
function restoreScroll(){if(previousOverflow!==null){document.documentElement.style.overflow=previousOverflow;previousOverflow=null;}}
function close(){dialog.value?.close();restoreScroll();}
function closed(){restoreScroll();trigger.value?.focus({preventScroll:true});}
watch(()=>props.src,()=>{close();failed.value=false;});
onBeforeUnmount(()=>{close();});
</script>
<template>
 <figure class="article-cover">
  <div class="article-cover-media" :class="{'is-unavailable':failed}">
  <button v-if="!failed" ref="trigger" type="button" class="article-cover-stage" aria-label="放大新闻图片" @click="enlarge">
   <img :src="imageUrl()" :alt="alt" decoding="async" @error="failed=true" />
  </button>
  <div v-else class="article-cover-unavailable" role="status"><span>{{sourceMark}}</span><small>图片暂不可用</small></div>
  </div>
  <dialog ref="dialog" class="article-cover-dialog" aria-label="新闻图片大图" @keydown.tab.prevent="closeButton?.focus()" @close="closed" @click="e=>{if(e.target===dialog)close();}">
   <button ref="closeButton" type="button" class="article-cover-close" aria-label="关闭大图" autofocus @click="close"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M6 18 18 6"/></svg></button>
   <img v-if="!failed" :src="imageUrl()" :alt="alt" decoding="async" />
  </dialog>
 </figure>
</template>
<style scoped>
.article-cover{margin:0 0 20px;width:100%;min-width:0}
.article-cover-media{width:fit-content;max-width:100%;margin-inline:auto}
.article-cover-stage{display:block;width:fit-content;max-width:100%;height:auto;margin:0;padding:0;border:0;border-radius:5px;background:transparent;overflow:hidden;cursor:zoom-in}
.article-cover-stage img{display:block;width:auto;height:auto;max-width:100%;max-height:min(48dvh,420px);object-fit:contain}
.article-cover :is(button,dialog):focus-visible{outline:2px solid var(--blue);outline-offset:3px}
.article-cover-media.is-unavailable{width:100%}
.article-cover-unavailable{display:grid;place-items:center;align-content:center;height:180px;gap:8px;background:#eeede7;border:1px solid var(--line);border-radius:8px;color:#64748b}
.article-cover-unavailable span{font:48px var(--serif)}
.article-cover-unavailable small{font-size:12px}
.article-cover-dialog{position:fixed;inset:0;max-width:none;max-height:none;width:100vw;height:100dvh;margin:0;padding:54px 20px 20px;border:0;background:#17212aee;overflow:auto;overscroll-behavior:contain;color:#fff}
.article-cover-dialog[open]{display:flex;align-items:center;justify-content:center}
.article-cover-dialog::backdrop{background:#17212aee}
.article-cover-dialog img{display:block;max-width:100%;max-height:calc(100dvh - 74px);width:auto;height:auto;object-fit:contain}
.article-cover-close{position:fixed;right:16px;top:12px;display:grid;place-items:center;width:40px;height:40px;border:1px solid #ffffff40;border-radius:50%;color:#fff;background:#26343f;z-index:1}
@media(max-width:760px){.article-cover-stage img{max-height:min(48dvh,360px)}.article-cover-dialog{padding:60px 10px 20px}.article-cover-dialog img{max-height:calc(100dvh - 80px)}}
</style>
