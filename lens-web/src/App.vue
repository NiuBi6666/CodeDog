<script setup>
import { computed,ref,watch,onMounted,onBeforeUnmount } from 'vue';
import { useRoute,useRouter } from 'vue-router';
import { syncMessage,syncExpired } from './sync-state.js';
import { relativeTime,groupStories } from './reading-utils.js';
import SelectionTranslator from './SelectionTranslator.vue';
const route=useRoute(),router=useRouter();
const categories=['全部','科技','国际','财经','教育'];
const articles=ref([]),selected=ref(null),category=ref('全部'),source=ref('全部来源'),query=ref(''),sourceList=ref([]),page=ref(0),total=ref(0);
const mode=ref('bilingual'),syncing=ref(false),notice=ref(''),imageFailed=ref(false),loading=ref(true),error=ref('');
const effectiveMode=computed(()=>mode.value),fontSize=ref(17),clock=ref(Date.now());
function dateLabel(value){return relativeTime(value,clock.value);}
function exactDate(value){return new Date(value).toLocaleString('zh-CN');}
const sources=computed(()=>['全部来源',...sourceList.value]);
const filtered=computed(()=>groupStories(articles.value));
let generation=0,detailGeneration=0,poll,debounce,clockTimer;
function normalize(a){const lines=v=>Array.isArray(v)?v:(v||'').split(/\n\n/).filter(Boolean);return {...a,source_mark:a.source_name?.slice(0,1)||'L',category_en:{科技:'TECH',国际:'WORLD',财经:'MARKETS',教育:'EDUCATION'}[a.category]||'NEWS',summary_zh:Array.isArray(a.summary_zh)?a.summary_zh:(a.summary_zh||'').split('\n').filter(Boolean),content_en:lines(a.content_en),content_zh:lines(a.content_zh),related_sources:a.related_sources||[]};}
async function request(path,options={}){const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);try{const r=await fetch('/api'+path,{...options,signal:controller.signal});const p=await r.json();if(!r.ok)throw Error(p.error||'请求失败');return p;}catch(e){throw Error(e.name==='AbortError'?'请求超时，请稍后重试':e.message);}finally{clearTimeout(timer);}}
async function detail(id){const g=++detailGeneration;try{const a=await request('/news/'+id);if(g===detailGeneration){selected.value=normalize(a);imageFailed.value=false;}}catch(e){if(g===detailGeneration){selected.value=null;error.value=e.message;}}}
async function load(append=false){const g=++generation;loading.value=true;error.value='';try{const p=await request('/news?'+new URLSearchParams({q:query.value,category:category.value==='全部'?'':category.value,source:source.value==='全部来源'?'':source.value,page:String(page.value)}));if(g!==generation)return;const rows=p.articles.map(normalize);articles.value=append?[...articles.value,...rows]:rows;total.value=p.total;sourceList.value=p.sources.map(s=>s.source_name);if(route.params.id){if(!selected.value||selected.value.id!==Number(route.params.id))await detail(route.params.id);}else if(articles.value.length){if(!selected.value||!articles.value.some(a=>a.id===selected.value.id))await detail(articles.value[0].id);}else selected.value=null;}catch(e){if(g===generation)error.value=e.message;}finally{if(g===generation)loading.value=false;}}
function selectArticle(id){router.push('/article/'+id);}
function more(){page.value++;load(true);}
function stopPoll(){clearTimeout(poll);poll=null;syncing.value=false;}
async function sync(){
 if(syncing.value)return;syncing.value=true;notice.value='正在提交采集任务…';
 try{
  const before=await request('/news/sync-status');
  const initial=Number(before.published_articles||0),started=Date.now();
  const p=await request('/user/refresh',{method:'POST'});notice.value=p.message;
  async function tick(){
   if(!syncing.value)return;
   try{
    const s=await request('/news/sync-status?run_id='+encodeURIComponent(p.run_id));
    await load();
    if(!syncing.value)return;
    notice.value=syncMessage(s,Math.max(0,Number(s.published_articles||0)-initial));
    if(['failed','uncertain','idle'].includes(s.phase)){stopPoll();return;}
    if(syncExpired(started)){notice.value='等待时间较长，后台仍会继续抓取和翻译；可稍后刷新列表。';stopPoll();return;}
    poll=setTimeout(tick,5000);
   }catch(e){notice.value='暂时无法查询进度，后台任务仍会继续；可稍后刷新。';stopPoll();}
  }
  poll=setTimeout(tick,5000);
 }catch(e){notice.value=e.message;stopPoll();}
}
function proxyImage(url){return 'https://wsrv.nl/?url='+encodeURIComponent(url);}
watch([category,source],()=>{page.value=0;load();});watch(query,()=>{clearTimeout(debounce);debounce=setTimeout(()=>{page.value=0;load();},350);});watch(()=>route.params.id,id=>{if(id)detail(id);else if(articles.value.length)detail(articles.value[0].id);});
onMounted(()=>{load();clockTimer=setInterval(()=>clock.value=Date.now(),60000);});onBeforeUnmount(()=>{stopPoll();clearTimeout(debounce);clearInterval(clockTimer);});
</script>

<template>
  <div class="site-shell" :class="{'article-open':route.params.id}">
    <header class="topbar">
      <div class="brand"><span class="brand-mark">L</span><div><strong>LENS</strong><small>视界 · GLOBAL EDITION</small></div></div>
      <div class="global-modes" aria-label="全局阅读模式"><button v-for="m in [{id:'zh',label:'中文'},{id:'bilingual',label:'双语'},{id:'en',label:'EN'}]" :key="m.id" :class="{active:mode===m.id}" @click="mode=m.id">{{m.label}}</button></div><nav class="category-nav" aria-label="领域分类"><button v-for="item in categories" :key="item" :class="{ active: category === item }" @click="category = item">{{ item }}</button></nav>
      <div class="top-actions"><label class="search"><span>⌕</span><input v-model="query" placeholder="搜索报道" aria-label="搜索报道" /></label><button class="sync-button" :disabled="syncing" @click="sync"><span :class="{ spin: syncing }">↻</span>{{ syncing ? "同步中" : "立即同步" }}</button><button class="mode-mini" @click="mode = mode === 'bilingual' ? 'zh' : mode === 'zh' ? 'en' : 'bilingual'">{{ mode === "bilingual" ? "中英" : mode === "zh" ? "中文" : "EN" }}</button></div>
    </header>
    <div v-if="notice" class="notice" role="status">{{ notice }}</div>
    <main class="workspace">
      <aside class="feed-panel">
        <div class="feed-heading"><h1>今日简报 <span class="count">({{ total }})</span></h1><select v-model="source" aria-label="按来源筛选"><option v-for="item in sources" :key="item">{{ item }}</option></select></div>
        <p v-if="error" class="feed-error" role="alert">{{error}} <button @click="load()">重试</button></p><p v-if="loading" class="feed-state" role="status">正在读取报道…</p><div class="feed-list">
          <div v-for="article in filtered" :key="article.id" class="story-group" :class="{selected:selected?.id===article.id||article.folded.some(item=>item.id===selected?.id)}">
            <button class="story-card" :aria-current="selected?.id===article.id?'true':undefined" @click="selectArticle(article.id)">
              <div class="story-meta"><span class="source-mark">{{ article.source_mark }}</span><span class="story-source">{{ article.source_name }}</span><span class="category-tag">{{ article.category }}</span><time :datetime="article.published_at" :title="exactDate(article.published_at)">{{ dateLabel(article.published_at) }}</time></div>
              <h2 :class="{'title-zh':mode!=='en'}">{{ mode==='en'?article.title_en:(article.title_zh||article.title_en) }}</h2>
              <p v-if="mode==='bilingual'&&article.title_zh&&article.title_zh!==article.title_en" class="story-en">{{ article.title_en }}</p>
              <p v-if="mode!=='en'&&article.summary_zh.length" class="story-summary">{{ article.summary_zh[0] }}</p>
            </button>
            <div v-if="article.folded.length" class="folded-sources"><span>同题报道</span><button v-for="item in article.folded" :key="item.id" :aria-current="selected?.id===item.id?'true':undefined" @click="selectArticle(item.id)">{{ item.source_name }} · {{ mode==='en'?item.title_en:(item.title_zh||item.title_en) }} ↗</button></div>
          </div>
          <div v-if="!loading && !error && !filtered.length" class="empty-state"><strong>暂时没有可阅读的报道</strong><span>调整筛选，或等待采集与翻译完成。这里仅展示真实入库的完整报道。</span></div><button v-if="articles.length<total" class="load-more" :disabled="loading" @click="more">加载更多报道</button>
        </div>
      </aside>
      <article v-if="selected" class="reader-panel"><button class="back-to-list" @click="router.push('/')">← 返回新闻列表</button>
        <div class="reader-toolbar"><span class="eyebrow">{{ selected.category_en }} / {{ selected.source_name }}</span><div class="reading-tools"><a :href="selected.origin_url" target="_blank" rel="noopener noreferrer">查看原网页 ↗</a><button :disabled="fontSize<=15" aria-label="缩小正文字号" @click="fontSize=Math.max(15,fontSize-1)">A−</button><button :disabled="fontSize>=22" aria-label="放大正文字号" @click="fontSize=Math.min(22,fontSize+1)">A+</button></div></div>
        <div class="reader-content"><div class="article-kicker"><span class="source-mark large">{{ selected.source_mark }}</span><span>{{ selected.source_name }}</span><span class="dot">·</span><span>{{ selected.author }}</span><span class="dot">·</span><time :datetime="selected.published_at" :title="exactDate(selected.published_at)">{{ dateLabel(selected.published_at) }}</time></div><h1 :data-selectable-original="effectiveMode==='en'?'':undefined" :class="{'title-zh':effectiveMode!=='en'}">{{ effectiveMode==='en' ? selected.title_en : (selected.title_zh || selected.title_en) }}</h1><p v-if="effectiveMode==='bilingual' && selected.title_zh && selected.title_zh!==selected.title_en" class="headline-en" data-selectable-original>{{ selected.title_en }}</p>
          <section v-if="effectiveMode!=='en'&&selected.summary_zh.length" class="takeaways" aria-label="核心导读"><h2>核心导读</h2><ul><li v-for="point in selected.summary_zh.slice(0,3)" :key="point">{{ point }}</li></ul></section>
          <div v-if="selected.cover_image" class="cover-wrap"><img v-if="selected.cover_image && !imageFailed" :src="proxyImage(selected.cover_image)" :alt="selected.title_en" @error="imageFailed = true" /><div v-else class="cover-fallback"><span>{{ selected.source_mark }}</span><small>IMAGE UNAVAILABLE</small></div></div>
          <div class="paragraphs" :style="{'--reading-size':fontSize+'px'}"><div v-for="(paragraph, index) in selected.content_zh" :key="paragraph" class="paragraph-pair" tabindex="0"><p v-if="effectiveMode !== 'zh'" class="en-paragraph" data-selectable-original lang="en">{{ selected.content_en[index] }}</p><p v-if="effectiveMode !== 'en'" class="zh-paragraph">{{ paragraph }}</p></div></div>
          <footer class="article-footer"><div><span class="eyebrow">RELATED COVERAGE</span><div class="related"><a v-for="item in selected.related_sources" :key="item.origin_url" :href="item.origin_url" target="_blank" rel="noopener noreferrer">{{ item.source_name }} ↗</a></div></div><a class="original-link" :href="selected.origin_url" target="_blank" rel="noopener noreferrer">查看原文 ↗</a></footer>
        </div>
        <SelectionTranslator :article-id="selected.id" />
      </article><section v-else class="reader-panel reader-empty"><span class="eyebrow">LENS · GLOBAL EDITION</span><h2>等待下一篇好报道</h2><p>新闻将在采集、正文校验和双语编译完成后出现。</p></section>
    </main>
  </div>
</template>
