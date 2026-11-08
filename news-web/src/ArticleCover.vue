<script setup>
import { ref,computed,watch,onBeforeUnmount } from 'vue';
const props=defineProps({src:{type:String,default:''},images:{type:Array,default:()=>[]},alt:{type:String,default:''},sourceMark:{type:String,default:'C'}});
const slides=computed(()=>[...new Set((props.images.length?props.images:[props.src]).filter(v=>typeof v==='string'&&v.startsWith('https://')))]);
const index=ref(0),failed=ref(false),dialog=ref(null),trigger=ref(null),closeButton=ref(null);
const current=computed(()=>slides.value[index.value]||'');
let previousOverflow=null,touchStart=null;
function imageUrl(){return 'https://wsrv.nl/?url='+encodeURIComponent(current.value);}
function move(delta){index.value=(index.value+delta+slides.value.length)%slides.value.length;failed.value=false;}
function touchEnd(event){if(touchStart===null)return;const t=event.changedTouches[0],dx=t.clientX-touchStart.x,dy=t.clientY-touchStart.y;if(slides.value.length>1&&Math.abs(dx)>50&&Math.abs(dx)>Math.abs(dy)*1.5)move(dx<0?1:-1);touchStart=null;}
function enlarge(){if(failed.value||!dialog.value||dialog.value.open)return;dialog.value.showModal();previousOverflow=document.documentElement.style.overflow;document.documentElement.style.overflow='hidden';}
function restoreScroll(){if(previousOverflow!==null){document.documentElement.style.overflow=previousOverflow;previousOverflow=null;}}
function close(){dialog.value?.close();restoreScroll();}
function closed(){restoreScroll();trigger.value?.focus({preventScroll:true});}
watch(()=>[props.src,props.images],()=>{close();index.value=0;failed.value=false;});
onBeforeUnmount(()=>close());
</script>
<template>
 <figure v-if="slides.length" class="article-cover" :aria-label="slides.length>1?'新闻图片轮播':'新闻图片'">
  <div class="article-cover-media" :class="{'is-unavailable':failed}" @touchstart.passive="e=>touchStart={x:e.touches[0].clientX,y:e.touches[0].clientY}" @touchend.passive="touchEnd">
   <button v-if="!failed" ref="trigger" type="button" class="article-cover-stage" aria-label="放大新闻图片" @click="enlarge">
    <img :key="current" :src="imageUrl()" :alt="alt" decoding="async" @error="failed=true" />
   </button>
   <div v-else class="article-cover-unavailable" role="status"><span>{{sourceMark}}</span><small>图片暂不可用</small></div>
  </div>
  <div v-if="slides.length>1" class="gallery-controls" role="group" aria-label="图片切换">
   <button type="button" aria-label="上一张图片" @click="move(-1)"><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m12 5-5 5 5 5"/></svg></button>
   <span aria-live="polite" aria-atomic="true">{{index+1}} / {{slides.length}}</span>
   <button type="button" aria-label="下一张图片" @click="move(1)"><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m8 5 5 5-5 5"/></svg></button>
  </div>
  <dialog ref="dialog" class="article-cover-dialog" aria-label="新闻图片大图" @keydown.tab.prevent="closeButton?.focus()" @keydown.left.prevent="slides.length>1&&move(-1)" @keydown.right.prevent="slides.length>1&&move(1)" @close="closed" @click="e=>{if(e.target===dialog)close();}">
   <button ref="closeButton" type="button" class="article-cover-close" aria-label="关闭大图" autofocus @click="close"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M6 18 18 6"/></svg></button>
   <img v-if="!failed" :key="current" :src="imageUrl()" :alt="alt" decoding="async" />
  </dialog>
 </figure>
</template>
<style scoped>
.article-cover{margin:0 0 20px;width:100%;min-width:0}
.article-cover-media{width:fit-content;max-width:100%;margin-inline:auto}
.article-cover-stage{display:block;width:fit-content;max-width:100%;height:auto;margin:0;padding:0;border:0;border-radius:18px;background:#ffffff40;box-shadow:0 8px 28px #31476b0a,inset 0 1px 1px #ffffffb3;overflow:hidden;cursor:zoom-in}
.article-cover-stage img{display:block;width:auto;height:auto;max-width:100%;max-height:min(48dvh,420px);object-fit:contain}
.article-cover :is(button,dialog):focus-visible{outline:2px solid var(--blue);outline-offset:3px}
.article-cover-media.is-unavailable{width:100%}
.article-cover-unavailable{display:grid;place-items:center;align-content:center;height:180px;gap:8px;background:linear-gradient(135deg,#dfeafa88,#ece5fb88);border:1px solid #ffffffc9;border-radius:18px;color:#64748b}
.article-cover-unavailable span{font:48px var(--serif)}
.article-cover-unavailable small{font-size:12px}
.article-cover-dialog{position:fixed;inset:0;max-width:none;max-height:none;width:100vw;height:100dvh;margin:0;padding:54px 20px 20px;border:0;background:#17212aee;overflow:auto;overscroll-behavior:contain;color:#fff}
.article-cover-dialog[open]{display:flex;align-items:center;justify-content:center}
.article-cover-dialog::backdrop{background:#17212aee}
.article-cover-dialog img{display:block;max-width:100%;max-height:calc(100dvh - 74px);width:auto;height:auto;object-fit:contain}
.article-cover-close{position:fixed;right:16px;top:12px;display:grid;place-items:center;width:40px;height:40px;border:1px solid #ffffff40;border-radius:50%;color:#fff;background:#ffffff24;backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);box-shadow:inset 0 1px 1px #ffffff40;z-index:1}
@media(max-width:760px){.article-cover-stage img{max-height:min(48dvh,360px)}.article-cover-dialog{padding:60px 10px 20px}.article-cover-dialog img{max-height:calc(100dvh - 80px)}}
.gallery-controls{display:flex;align-items:center;justify-content:center;gap:12px;margin-top:10px;color:var(--muted);font-size:11px;font-variant-numeric:tabular-nums}
.gallery-controls button{display:grid;place-items:center;width:30px;height:30px;padding:0;border:1px solid #ffffff99;border-radius:50%;background:#ffffff55;box-shadow:inset 0 1px 1px #ffffffaa;color:var(--ink)}
.gallery-controls svg{width:17px;height:17px;fill:none;stroke:currentColor;stroke-width:1.5;stroke-linecap:round;stroke-linejoin:round}
.gallery-controls span{min-width:42px;text-align:center}
</style>
