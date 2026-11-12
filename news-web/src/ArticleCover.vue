<script setup>
import { ref,computed,watch,onBeforeUnmount } from 'vue';
import { loadNewsImage,isNewsImage } from './news-images.js';
const props=defineProps({src:{type:String,default:''},images:{type:Array,default:()=>[]},alt:{type:String,default:''},sourceMark:{type:String,default:'C'}});
const slides=computed(()=>[...new Set((props.images.length?props.images:[props.src]).filter(isNewsImage))]);
const index=ref(0),failed=ref(false),dialog=ref(null),trigger=ref(null),closeButton=ref(null),switching=ref(false),switchError=ref('');
const current=computed(()=>slides.value[index.value]||'');
let previousOverflow=null,touchStart=null,switchTicket=0,targetIndex=0;
const resolved=ref(''),original=ref(''),loaded=ref(false),zoomFailed=ref(false),zoom=ref(1),pan=ref({x:0,y:0});
let drag=null;
let imageTicket=0;
function resetArticle(){imageTicket++;resolved.value='';loaded.value=false;}
function preload(i){const url=slides.value[i];return url?loadNewsImage(url):Promise.resolve('');}
function neighbors(){if(slides.value.length>1)for(const i of [(index.value+1)%slides.value.length,(index.value+slides.value.length-1)%slides.value.length])preload(i).catch(()=>{});}
function imageUrl(){return resolved.value;}
async function prepare(){const ticket=++imageTicket;if(!resolved.value)loaded.value=false;failed.value=false;original.value='';zoomFailed.value=false;resetZoom();try{const url=await preload(index.value);if(ticket!==imageTicket)return;if(resolved.value!==url)loaded.value=false;resolved.value=url;if(dialog.value?.open)loadOriginal();}catch(e){if(ticket===imageTicket)failed.value=true;}}
function imageLoaded(event){if(event.target.currentSrc!==new URL(resolved.value,location.href).href)return;loaded.value=true;neighbors();}
function handleImageError(event){if(event.target.currentSrc!==new URL(resolved.value,location.href).href)return;failed.value=true;loaded.value=false;}
watch(current,prepare,{immediate:true});
async function move(delta){targetIndex=(targetIndex+delta+slides.value.length)%slides.value.length;const next=targetIndex,ticket=++switchTicket;switching.value=true;switchError.value='';try{await preload(next);if(ticket!==switchTicket)return;index.value=next;failed.value=false;neighbors();}catch(e){if(ticket===switchTicket){targetIndex=index.value;switchError.value='图片加载失败，请重试';}}finally{if(ticket===switchTicket)switching.value=false;}}
function touchEnd(event){if(touchStart===null)return;const t=event.changedTouches[0],dx=t.clientX-touchStart.x,dy=t.clientY-touchStart.y;if(slides.value.length>1&&Math.abs(dx)>50&&Math.abs(dx)>Math.abs(dy)*1.5)move(dx<0?1:-1);touchStart=null;}
function resetZoom(){zoom.value=1;pan.value={x:0,y:0};drag=null;}
function setZoom(value){zoom.value=Math.min(4,Math.max(1,value));if(zoom.value===1)pan.value={x:0,y:0};}
function wheelZoom(event){setZoom(zoom.value*Math.exp(-Math.max(-100,Math.min(100,event.deltaY))*.003));}
function dragStart(event){if(zoom.value<=1||event.button!==0)return;drag={x:event.clientX-pan.value.x,y:event.clientY-pan.value.y};event.currentTarget.setPointerCapture(event.pointerId);}
function dragMove(event){if(drag)pan.value={x:event.clientX-drag.x,y:event.clientY-drag.y};}
async function loadOriginal(){const url=current.value;try{const full=await loadNewsImage(url,true);if(current.value===url&&dialog.value?.open)original.value=full;}catch(e){if(current.value===url)zoomFailed.value=true;}}
async function enlarge(){if(failed.value||!loaded.value||!dialog.value||dialog.value.open)return;resetZoom();dialog.value.showModal();previousOverflow=document.documentElement.style.overflow;document.documentElement.style.overflow='hidden';loadOriginal();}

function restoreScroll(){if(previousOverflow!==null){document.documentElement.style.overflow=previousOverflow;previousOverflow=null;}}
function close(){dialog.value?.close();resetZoom();restoreScroll();}
function closed(){restoreScroll();trigger.value?.focus({preventScroll:true});}
watch(()=>slides.value.join('|'),()=>{close();resetArticle();switchTicket++;targetIndex=0;index.value=0;failed.value=false;switching.value=false;switchError.value='';prepare();},{immediate:true});
onBeforeUnmount(()=>{imageTicket++;close();});
</script>
<template>
 <figure v-if="slides.length" class="article-cover" :aria-label="slides.length>1?'新闻图片轮播':'新闻图片'">
  <div class="article-cover-media" :class="{'is-unavailable':failed,'is-single':slides.length===1,'has-image':loaded}" @touchstart.passive="e=>touchStart={x:e.touches[0].clientX,y:e.touches[0].clientY}" @touchend.passive="touchEnd">
   <div v-if="!loaded&&!failed" class="image-skeleton" role="status" aria-label="图片正在加载中"><span>图片正在加载中</span></div>
   <button v-if="!failed" ref="trigger" type="button" class="article-cover-stage" :aria-busy="!loaded" aria-label="放大新闻图片" @click="enlarge">
    <img v-if="resolved" :key="resolved" v-show="loaded" :src="imageUrl()" alt="" :class="{'image-ready':loaded}" decoding="async" @load="imageLoaded" @error="handleImageError" />
   </button>
   <div v-else class="article-cover-unavailable" role="status"><small>图片暂不可用</small><button type="button" @click="prepare">重试</button></div>
  <div v-if="slides.length>1" class="gallery-controls" role="group" aria-label="图片切换">
   <button type="button" aria-label="上一张图片" @click="move(-1)"><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m12 5-5 5 5 5"/></svg></button>
   <span :aria-busy="switching" aria-live="polite" aria-atomic="true">{{index+1}} / {{slides.length}}</span>
   <button type="button" aria-label="下一张图片" @click="move(1)"><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m8 5 5 5-5 5"/></svg></button>
  </div>
  </div>
  <small v-if="switching||switchError" class="gallery-status" role="status">{{switchError||'正在加载图片…'}}</small>
  <dialog ref="dialog" class="article-cover-dialog" aria-label="新闻图片大图" @keydown.tab.prevent="closeButton?.focus()" @keydown="e=>{if(['+','=','-','0'].includes(e.key)){e.preventDefault();if(e.key==='0')resetZoom();else setZoom(zoom+(e.key==='-'?-.25:.25));}}" @keydown.left.prevent="slides.length>1&&move(-1)" @keydown.right.prevent="slides.length>1&&move(1)" @close="closed" @click="e=>{if(e.target===dialog)close();}">
   <button ref="closeButton" type="button" class="article-cover-close" aria-label="关闭大图" autofocus @click="close"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M6 18 18 6"/></svg></button>
   <div class="lightbox-canvas" @click="e=>{if(e.target===e.currentTarget&&zoom===1)close();}" :class="{'is-zoomed':zoom>1}" @wheel.prevent="wheelZoom" @pointerdown="dragStart" @pointermove="dragMove" @pointerup="drag=null" @pointercancel="drag=null" @lostpointercapture="drag=null" @dblclick="zoom>1?resetZoom():setZoom(2)">
    <img v-if="!failed&&resolved" :src="original||imageUrl()" :alt="alt" :style="{transform:'translate('+pan.x+'px,'+pan.y+'px) scale('+zoom+')'}" decoding="async" draggable="false" />
   </div>
   <small v-if="zoomFailed" role="status">原图暂不可用</small>
  </dialog>
 </figure>
</template>
<style scoped>
.article-cover{position:relative;margin:0 0 20px;width:100%;min-width:0}
.article-cover-media{position:relative;width:100%;aspect-ratio:16/9;border-radius:18px;isolation:isolate;overflow:hidden;background:rgba(255,255,255,.5);box-shadow:0 8px 28px #31476b0a,inset 0 1px 1px #ffffffb3}
.article-cover-stage{position:relative;z-index:1;display:flex;align-items:center;justify-content:center;width:100%;height:100%;margin:0;padding:0;border:0;border-radius:18px;background:transparent;box-shadow:none;overflow:hidden;cursor:zoom-in}
.article-cover-stage img{opacity:0;transition:opacity .2s;display:block;width:100%;height:100%;object-fit:cover;object-position:center}
.article-cover-stage img.image-ready{opacity:1}
.image-skeleton{position:absolute;inset:0;z-index:2;pointer-events:none;display:grid;place-items:center;color:#526174;font-size:13px;background:linear-gradient(100deg,#ffffff30 20%,#ffffffa0 48%,#ffffff30 76%);background-size:200% 100%;animation:image-shimmer 1.5s linear infinite}
@keyframes image-shimmer{from{background-position:200% 0}to{background-position:-200% 0}}
@media(prefers-reduced-motion:reduce){.image-skeleton{animation:none}.article-cover-stage img{transition:none}}
.article-cover :is(button,dialog):focus-visible{outline:2px solid var(--blue);outline-offset:3px}
.article-cover-media.is-unavailable{aspect-ratio:auto;min-height:72px}
.article-cover-unavailable button{border:1px solid #ffffffb3;border-radius:10px;padding:5px 12px;background:#ffffff80;color:var(--blue)}
.article-cover-unavailable{display:grid;place-items:center;align-content:center;min-height:72px;gap:8px;background:linear-gradient(135deg,#dfeafa88,#ece5fb88);border:1px solid #ffffffc9;border-radius:18px;color:#64748b}
.article-cover-unavailable span{font:48px var(--serif)}
.article-cover-unavailable small{font-size:12px}
.article-cover-dialog{position:fixed;inset:0;max-width:none;max-height:none;width:100vw;height:100dvh;margin:0;padding:54px 20px 20px;border:0;background:#17212aee;overflow:hidden;overscroll-behavior:contain;color:#fff}
.article-cover-dialog[open]{display:flex;align-items:center;justify-content:center}
.article-cover-dialog::backdrop{background:#17212aee}
.lightbox-canvas{display:flex;align-items:center;justify-content:center;width:100%;height:100%;overflow:hidden;touch-action:none;cursor:zoom-in}
.lightbox-canvas.is-zoomed{cursor:grab}.lightbox-canvas.is-zoomed:active{cursor:grabbing}
.article-cover-dialog img{user-select:none;display:block;max-width:100%;max-height:calc(100dvh - 74px);width:auto;height:auto;object-fit:contain}
.article-cover-close{position:fixed;right:16px;top:12px;display:grid;place-items:center;width:40px;height:40px;border:1px solid #ffffff40;border-radius:50%;color:#fff;background:#ffffff24;backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);box-shadow:inset 0 1px 1px #ffffff40;z-index:1}
@media(max-width:760px){.article-cover-dialog{padding:60px 10px 20px}.lightbox-canvas{display:flex;align-items:center;justify-content:center;width:100%;height:100%;overflow:hidden;touch-action:none;cursor:zoom-in}
.lightbox-canvas.is-zoomed{cursor:grab}.lightbox-canvas.is-zoomed:active{cursor:grabbing}
.article-cover-dialog img{user-select:none;max-height:calc(100dvh - 80px)}}
 .gallery-controls{z-index:2;position:absolute;inset:0;pointer-events:none;color:#fff;font-size:11px;font-variant-numeric:tabular-nums}
.gallery-controls button{position:absolute;top:50%;transform:translateY(-50%);display:grid;place-items:center;width:36px;height:36px;padding:0;border:1px solid #ffffff80;border-radius:50%;background:rgba(255,255,255,.3);backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);box-shadow:0 3px 12px #17212a26,inset 0 1px 1px #ffffff80;text-shadow:0 1px 3px #17212a80;color:#fff;pointer-events:auto;cursor:pointer}
.gallery-controls button:first-child{left:12px}
.gallery-controls button:last-child{right:12px}
.gallery-controls button:hover{background:rgba(255,255,255,.46)}
.gallery-controls svg{width:18px;height:18px;fill:none;stroke:currentColor;stroke-width:1.75;stroke-linecap:round;stroke-linejoin:round}
.gallery-controls span{position:absolute;bottom:14px;left:50%;transform:translateX(-50%);min-width:44px;padding:4px 10px;border-radius:999px;text-align:center;font-size:12px;background:rgba(0,0,0,.45);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px)}
.gallery-status{position:absolute;left:50%;bottom:40px;transform:translateX(-50%);max-width:90%;padding:4px 9px;border-radius:12px;background:#17212abb;color:#fff;font-size:11px;pointer-events:none}
@media(max-width:760px){.gallery-controls button{width:32px;height:32px}.gallery-controls button:first-child{left:8px}.gallery-controls button:last-child{right:8px}}
</style>
