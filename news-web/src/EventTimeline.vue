<script setup>
import {ref,watch} from 'vue';
import {useRoute} from 'vue-router';
const route=useRoute(),events=ref([]),event=ref(null),loading=ref(false),error=ref(''),descending=ref(false);
const stages={background:'背景',development:'进展',response:'回应',outcome:'结果'};
let version=0;
async function load(){
 const g=++version;loading.value=true;error.value='';event.value=null;
 try{
  const r=await fetch('/api/news/events'+(route.params.eventId?'/'+route.params.eventId:''));const data=await r.json();
  if(!r.ok)throw Error(data.error||'读取失败');if(g!==version)return;
  if(route.params.eventId)event.value=data;else events.value=data.events;
 }catch(e){if(g===version)error.value=e.message;}finally{if(g===version)loading.value=false;}
}
const date=v=>new Date(v).toLocaleString('zh-CN',{year:'numeric',month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'});
watch(()=>route.fullPath,load,{immediate:true});
</script>
<template>
 <section class="events-page">
  <div class="events-heading"><RouterLink to="/">新闻</RouterLink><RouterLink v-if="event" to="/events">全部事件</RouterLink><span v-if="event">{{event.category}}</span></div>
  <p v-if="loading" role="status">正在读取事件…</p>
  <p v-if="error" role="alert">{{error}} <button @click="load">重试</button></p>
  <template v-if="event">
   <h1>{{event.title}}</h1>
   <div class="event-meta"><span>{{event.article_count}} 篇报道</span><button @click="descending=!descending">{{descending?'最新在前':'时间正序'}} ↕</button></div>
   <ol class="event-timeline">
    <li v-for="node in (descending?[...event.nodes].reverse():event.nodes)" :key="node.id">
     <div class="event-node-meta"><time>{{date(node.occurred_at)}}</time><span>{{node.time_basis==='occurred'?'事件发生':'报道发布'}}</span><span>{{stages[node.stage]}}</span></div>
     <h2>{{node.title}}</h2>
     <div v-for="report in node.reports" :key="report.article_id" class="event-report">
      <p v-if="report.lang!=='zh'" lang="en">{{report.title_en}}</p>
      <RouterLink :to="'/article/'+report.article_id">{{report.title_zh||report.title_en}}</RouterLink>
      <div><span>{{report.source_name}} · {{date(report.published_at)}}</span><a :href="report.origin_url" target="_blank" rel="noopener noreferrer">原文 ↗</a></div>
     </div>
    </li>
   </ol>
   <p v-if="!event.nodes.length">暂无已确认的进展</p>
  </template>
  <template v-else-if="!loading&&!error">
   <h1>事件追踪</h1>
   <div class="event-grid"><RouterLink v-for="item in events" :key="item.id" :to="'/event/'+item.id" class="event-card"><span>{{item.category}} · {{item.article_count}} 篇报道</span><h2>{{item.title}}</h2><time v-if="item.last_at">更新于 {{date(item.last_at)}}</time></RouterLink></div>
   <p v-if="!events.length">暂无公开事件</p>
  </template>
 </section>
</template>
