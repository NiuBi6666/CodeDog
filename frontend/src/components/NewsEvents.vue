<script setup>
import {ref,onMounted} from 'vue';
import {api,jsonBody,notify} from '../api';
import {hasPermission} from '../auth';
const items=ref([]),current=ref(null),error=ref(''),busy=ref(false),title=ref(''),category=ref('国际'),query=ref(''),results=ref([]);
const categories=['国际','军事','科技','财经','教育','文娱','生活'],stage=ref('development'),occurred=ref(''),nodeId=ref('');
const stages={background:'背景',development:'进展',response:'回应',outcome:'结果'};
const date=v=>v?new Date(v).toLocaleString('zh-CN'):'';
async function action(fn){if(busy.value)return;busy.value=true;error.value='';try{await fn();}catch(e){error.value=e.message;}finally{busy.value=false;}}
async function load(){items.value=(await api('/admin/news/events')).events;}
async function open(item){current.value=await api('/admin/news/events/'+item.id);title.value=current.value.title;category.value=current.value.category;results.value=[];nodeId.value='';occurred.value='';}
function fresh(){current.value=null;title.value='';category.value='国际';results.value=[];}
async function save(){await action(async()=>{current.value=await api('/admin/news/events'+(current.value?'/'+current.value.id:''),{method:current.value?'PATCH':'POST',body:jsonBody({title:title.value,category:category.value})});await load();notify('事件已保存');});}
async function publish(){await action(async()=>{current.value=await api('/admin/news/events/'+current.value.id+'/status',{method:'PUT',body:jsonBody({status:current.value.status==='published'?'draft':'published'})});await load();});}
async function search(){await action(async()=>{results.value=(await api('/admin/news/event-articles?q='+encodeURIComponent(query.value))).articles;});}
async function link(article,state='confirmed'){
 await action(async()=>{
  const payload={state,stage:stage.value,...(nodeId.value?{node_id:Number(nodeId.value)}:{}),...(occurred.value?{occurred_at:new Date(occurred.value).toISOString()}:{})};
  current.value=await api('/admin/news/events/'+current.value.id+'/articles/'+(article.article_id||article.id),{method:'PUT',body:jsonBody(payload)});await load();
 });
}
onMounted(()=>action(load));
</script>
<template>
 <section class="admin-panel news-section">
  <div class="panel-heading"><h2>事件追踪</h2><div class="button-row"><button class="button button-quiet" :disabled="busy" @click="action(load)">刷新</button><button v-if="hasPermission('news.events.create')" class="button button-primary" @click="fresh">新建事件</button></div></div>
  <p v-if="error" class="notice notice-error" role="alert">{{error}}</p>
  <div class="news-events-workspace">
   <nav class="news-events-list" aria-label="事件列表"><button v-for="item in items" :key="item.id" :class="{active:current?.id===item.id}" @click="action(()=>open(item))"><strong>{{item.title}}</strong><small>{{item.status==='published'?'已发布':'草稿'}} · {{item.article_count}} 篇 · {{item.suggested_count}} 待确认</small></button><p v-if="!items.length">暂无事件</p></nav>
   <div class="news-event-editor">
    <form v-if="hasPermission(current?'news.events.update':'news.events.create')" class="form-stack" @submit.prevent="save"><label>事件名称<input v-model.trim="title" required maxlength="300"></label><label>分类<select v-model="category"><option v-for="c in categories" :key="c">{{c}}</option></select></label><div class="button-row"><button class="button button-primary" :disabled="busy">保存</button><button v-if="current&&hasPermission('news.events.publish')" type="button" class="button button-quiet" :disabled="busy" @click="publish">{{current.status==='published'?'撤下':'发布'}}</button><a v-if="current?.status==='published'" :href="'/news/event/'+current.id" target="_blank" rel="noopener">查看时间轴 ↗</a></div></form>
    <template v-if="current">
     <h3>{{current.title}}</h3>
     <form v-if="hasPermission('news.events.associate')" class="form-stack" @submit.prevent="search"><label>查找报道<input v-model.trim="query" minlength="2" maxlength="100" required></label><button class="button button-quiet" :disabled="busy">搜索</button><div class="news-form-grid"><label>阶段<select v-model="stage"><option v-for="(text,key) in stages" :value="key" :key="key">{{text}}</option></select></label><label>归入节点<select v-model="nodeId"><option value="">新建节点</option><option v-for="n in current.nodes" :key="n.id" :value="n.id">{{n.title}}</option></select></label><label v-if="!nodeId">事件发生时间（选填）<input v-model="occurred" type="datetime-local"></label></div></form>
     <div v-for="a in results" :key="a.id" class="news-event-report"><strong>{{a.title_zh||a.title_en}}</strong><small>{{a.source_name}} · {{date(a.published_at)}}</small><button v-if="hasPermission('news.events.associate')" class="button button-quiet button-small" :disabled="busy" @click="link(a)">关联报道</button></div>
     <h3 v-if="current.suggestions.length">待确认报道</h3>
     <div v-for="a in current.suggestions" :key="a.article_id" class="news-event-report"><a :href="'/news/article/'+a.article_id" target="_blank" rel="noopener">{{a.title_zh||a.title_en}}</a><small>{{a.source_name}} · {{date(a.published_at)}}</small><div v-if="hasPermission('news.events.associate')" class="button-row"><button class="button button-quiet button-small" :disabled="busy" @click="link(a)">确认关联</button><button class="button button-quiet button-small" :disabled="busy" @click="link(a,'excluded')">排除</button></div></div>
     <h3>时间轴 · {{current.article_count}} 篇</h3>
     <div v-for="n in current.nodes" :key="n.id" class="news-event-report"><small>{{date(n.occurred_at)}} · {{n.time_basis==='occurred'?'事件发生':'报道发布'}} · {{stages[n.stage]}}</small><strong>{{n.title}}</strong><div v-for="a in n.reports" :key="a.article_id"><a :href="'/news/article/'+a.article_id" target="_blank" rel="noopener">{{a.source_name}} · {{a.title_zh||a.title_en}}</a><div v-if="hasPermission('news.events.associate')" class="button-row"><button class="button button-quiet button-small" :disabled="busy" @click="link(a)">调整时间 / 阶段</button><button class="button button-quiet button-small" :disabled="busy" @click="link(a,'excluded')">移出事件</button></div></div></div>
    </template>
   </div>
  </div>
 </section>
</template>
<style scoped>
.news-events-workspace{display:grid;grid-template-columns:280px minmax(0,1fr)}.news-events-list{border-right:1px solid #e1e7e9;max-height:900px;overflow:auto;padding:12px}.news-events-list>button{display:block;text-align:left;width:100%;padding:12px;border:0;border-bottom:1px solid #e1e7e9;background:transparent;color:inherit}.news-events-list>button.active{background:#edf3f5}.news-events-list small,.news-event-report small{display:block;color:#64748b;margin-top:6px}.news-event-editor{padding:20px;min-width:0}.news-event-report{padding:14px 0;border-bottom:1px solid #e1e7e9;overflow-wrap:anywhere}.news-event-report a{color:#365b70}.news-event-report .button-row{margin-top:8px}@media(max-width:800px){.news-events-workspace{grid-template-columns:1fr}.news-events-list{max-height:260px;border-right:0;border-bottom:1px solid #e1e7e9}}
</style>
