<script setup>
import {ref,watch} from 'vue';
import {relativeTime} from './reading-utils.js';
const props=defineProps({article:{type:Object,required:true},mode:{type:String,default:'bilingual'},image:{type:Boolean,default:false}});
defineEmits(['open']);
const failed=ref(false);watch(()=>props.article.id,()=>failed.value=false);
</script>
<template>
 <article class="press-story">
  <div class="press-byline"><span>{{article.category}}</span><span>{{article.source_name}}</span><time :datetime="article.published_at">{{relativeTime(article.published_at)}}</time></div>
  <a :href="'/newspaper?article='+article.id" @click.prevent="$emit('open',article.id)">
   <img v-if="image&&article.cover_image&&!failed" class="press-thumb" :src="'https://wsrv.nl/?url='+encodeURIComponent(article.cover_image)" alt="" loading="lazy" @error="failed=true">
   <p v-if="mode==='bilingual'&&article.lang!=='zh'&&article.title_zh&&article.title_zh!==article.title_en" class="press-original" lang="en">{{article.title_en}}</p>
   <h2>{{mode==='en'?(article.title_original||article.title_en):(article.title_zh||article.title_original||article.title_en)}}</h2>
  </a>
 </article>
</template>
