<script setup>
import {ref,computed,watch,nextTick,onMounted,onBeforeUnmount} from 'vue';
import {relativeTime} from './reading-utils.js';
import {loadStoryExcerpt,clipExcerpt} from './newspaper-excerpts.js';
const props=defineProps({article:{type:Object,required:true},mode:{type:String,default:'bilingual'},image:{type:Boolean,default:false}});
defineEmits(['open']);
const failed=ref(false),root=ref(null),pairs=ref([]),loading=ref(false),error=ref(false);
const needsExcerpt=computed(()=>props.image&&(!props.article.cover_image||failed.value));
const original=computed(()=>clipExcerpt(pairs.value.map(p=>p.original).join(' '),props.article.lang==='zh'?100:props.mode==='en'?420:240));
const translated=computed(()=>clipExcerpt(pairs.value.map(p=>p.translation).filter(Boolean).join(' '),100));
const effectiveMode=computed(()=>props.article.lang==='zh'?'zh':props.mode);
let observer,ticket=0,disposed=false;
async function load(){
 const current=++ticket;observer?.disconnect();loading.value=true;error.value=false;
 try{const result=await loadStoryExcerpt(props.article);if(current===ticket&&!disposed)pairs.value=result;}
 catch{if(current===ticket&&!disposed)error.value=true;}
 finally{if(current===ticket&&!disposed)loading.value=false;}
}
async function observe(){
 observer?.disconnect();await nextTick();
 if(disposed||!needsExcerpt.value||!root.value)return;
 observer=new IntersectionObserver(entries=>{if(entries.some(e=>e.isIntersecting))load();},{rootMargin:'120px'});
 observer.observe(root.value);
}
watch(()=>props.article.id,()=>{ticket++;failed.value=false;pairs.value=[];error.value=false;loading.value=false;observe();});
watch(needsExcerpt,observe);
onMounted(observe);
onBeforeUnmount(()=>{disposed=true;ticket++;observer?.disconnect();});
</script>
<template>
 <article ref="root" class="press-story" :class="{'press-story-text':needsExcerpt}">
  <div class="press-byline"><span>{{article.category}}</span><span>{{article.source_name}}</span><time :datetime="article.published_at">{{relativeTime(article.published_at)}}</time></div>
  <a :href="'/newspaper?article='+article.id" @click.prevent="$emit('open',article.id)">
   <img v-if="image&&article.cover_image&&!failed" class="press-thumb" :src="'https://wsrv.nl/?url='+encodeURIComponent(article.cover_image)" alt="" loading="lazy" @error="failed=true">
   <p v-if="mode==='bilingual'&&article.lang!=='zh'&&article.title_zh&&article.title_zh!==article.title_en" class="press-original" lang="en">{{article.title_en}}</p>
   <h2>{{mode==='en'?(article.title_original||article.title_en):(article.title_zh||article.title_original||article.title_en)}}</h2>
   <div v-if="needsExcerpt&&(original||translated)" class="press-card-excerpt">
    <p v-if="effectiveMode!=='zh'||!translated" :lang="article.lang==='zh'?'zh-CN':'en'">{{original}}</p>
    <p v-if="effectiveMode!=='en'&&translated" lang="zh-CN">{{translated}}</p>
   </div>
  </a>
  <span v-if="needsExcerpt&&loading" class="press-excerpt-status" role="status">正在读取导语…</span>
  <button v-if="needsExcerpt&&error" type="button" class="press-excerpt-retry" @click="load">重试导语</button>
 </article>
</template>
