<script setup>
import { computed,ref,watch,nextTick,onMounted,onBeforeUnmount } from 'vue';
import { useRoute,useRouter } from 'vue-router';
import { relativeTime,groupStories } from './reading-utils.js';
import SelectionTranslator from './SelectionTranslator.vue';
import BackToTop from './BackToTop.vue';
import ArticleCover from './ArticleCover.vue';
const route=useRoute(),router=useRouter();
const categories=['全部','国际','军事','科技','财经','教育','文娱','生活'];
const articles=ref([]),selected=ref(null),category=ref('全部'),source=ref('全部来源'),query=ref(''),sourceList=ref([]),total=ref(0);
const readerPanel=ref(null),feedPanel=ref(null),loadSentinel=ref(null),hasMore=ref(false),nextCursor=ref(''),loadingMore=ref(false),moreError=ref('');
const bodyError=ref(''),bodyWaiting=ref(false);
const mode=ref('bilingual'),loading=ref(true),error=ref('');
const effectiveMode=computed(()=>selected.value?.lang==='zh'?'zh':mode.value),fontSize=ref(17),clock=ref(Date.now());
function dateLabel(value){return relativeTime(value,clock.value);}
function exactDate(value){return new Date(value).toLocaleString('zh-CN');}
const sources=computed(()=>['全部来源',...sourceList.value]);
const filtered=computed(()=>groupStories(articles.value));
let generation=0,detailGeneration=0,debounce,clockTimer,detailPoll,feedObserver;
function normalize(a){const lines=v=>Array.isArray(v)?v:(v||'').split(/\n\n/).filter(Boolean);return {...a,lang:a.lang||'en',title_en:a.title_original||a.title_en,source_mark:a.source_name?.slice(0,1)||'C',category_en:{国际:'WORLD',军事:'DEFENCE',科技:'TECH',财经:'MARKETS',教育:'EDUCATION',文娱:'CULTURE & SPORT',生活:'LIFESTYLE'}[a.category]||'NEWS',summary_zh:Array.isArray(a.summary_zh)?a.summary_zh:(a.summary_zh||'').split('\n').filter(Boolean),content_en:lines(a.content_original||a.content_en),content_zh:Array.isArray(a.paragraphs_zh)?a.paragraphs_zh:lines(a.content_zh),related_sources:a.related_sources||[]};}
async function request(path,options={}){const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);try{const r=await fetch('/api'+path,{...options,signal:controller.signal});const p=await r.json();if(!r.ok)throw Error(p.error||'请求失败');return p;}catch(e){throw Error(e.name==='AbortError'?'请求超时，请稍后重试':e.message);}finally{clearTimeout(timer);}}
function stopDetail(){clearTimeout(detailPoll);detailPoll=null;bodyWaiting.value=false;}
async function detail(id){
 const g=++detailGeneration;stopDetail();bodyError.value='';selected.value=null;
 try{
  const a=await request('/news/'+id);if(g!==detailGeneration)return;
  selected.value=normalize(a);
  if(a.translation_status==='ready')return;
  const csrf=await request('/auth/csrf');
  if(g!==detailGeneration)return;
  await request('/news/'+id+'/translation',{method:'POST',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':csrf.token},body:'{}'});
  if(g!==detailGeneration)return;
  bodyWaiting.value=true;
  const started=Date.now();
  async function tick(){
   if(g!==detailGeneration)return;
   try{
    const next=await request('/news/'+id);if(g!==detailGeneration)return;
    selected.value=normalize(next);
    if(['ready','failed'].includes(next.translation_status)){bodyWaiting.value=false;return;}
    if(Date.now()-started>=600000){bodyWaiting.value=false;bodyError.value='仍在后台翻译';return;}
    bodyError.value='';detailPoll=setTimeout(tick,1000);
   }catch(e){if(g===detailGeneration){bodyError.value='连接恢复中';if(Date.now()-started<600000)detailPoll=setTimeout(tick,3000);else bodyWaiting.value=false;}}
  }
  detailPoll=setTimeout(tick,1000);
 }catch(e){if(g===detailGeneration){bodyWaiting.value=false;if(selected.value)bodyError.value=e.message;else error.value=e.message;}}
}
function listPath(cursor=''){
 return '/news?'+new URLSearchParams({q:query.value,category:category.value==='全部'?'':category.value,source:source.value==='全部来源'?'':source.value,...(cursor?{cursor}:{})});
}
function applyPage(p,append=false){
 const rows=p.articles.map(normalize);
 const existing=append?articles.value:[];
 const ids=new Set(existing.map(a=>a.id));
 articles.value=[...existing,...rows.filter(a=>!ids.has(a.id))];
 total.value=p.total;sourceList.value=p.sources.map(s=>s.source_name);
 hasMore.value=p.has_more===true;nextCursor.value=p.next_cursor||'';
 return rows;
}
async function load(){
 const g=++generation;loading.value=true;loadingMore.value=false;error.value='';moreError.value='';
 try{
  const p=await request(listPath());if(g!==generation)return;
  const rows=applyPage(p);
  if(route.params.id){if(!selected.value||selected.value.id!==Number(route.params.id))await detail(route.params.id);}else selected.value=null;
  return rows;
 }catch(e){if(g===generation)error.value=e.message;return null;}
 finally{if(g===generation){loading.value=false;observeFeed();}}
}
function selectArticle(id){router.push('/article/'+id);}
function selectCategory(item){category.value=item;}
async function more(){
 if(loading.value||loadingMore.value||moreError.value||!hasMore.value||!nextCursor.value)return;
 const g=generation,cursor=nextCursor.value;
 loadingMore.value=true;
 try{
  const p=await request(listPath(cursor));if(g!==generation)return;
  applyPage(p,true);
 }catch(e){if(g===generation)moreError.value=e.message;}
 finally{if(g===generation){loadingMore.value=false;observeFeed();}}
}
function retryMore(){moreError.value='';more();}
async function observeFeed(){
 feedObserver?.disconnect();await nextTick();
 if(!loadSentinel.value||loading.value||loadingMore.value||moreError.value||!hasMore.value)return;
 const mobile=matchMedia('(max-width:760px)').matches;
 if(mobile&&route.params.id)return;
 feedObserver=new IntersectionObserver(entries=>{if(entries.some(e=>e.isIntersecting))more();},{root:mobile?null:feedPanel.value,rootMargin:'0px 0px 120px 0px',threshold:0});
 feedObserver.observe(loadSentinel.value);
}
function scrollListTop(){
 if(matchMedia('(max-width:760px)').matches)window.scrollTo({top:0,behavior:'instant'});
 else if(feedPanel.value)feedPanel.value.scrollTop=0;
}
watch([category,source],()=>{scrollListTop();load();});watch(query,()=>{clearTimeout(debounce);debounce=setTimeout(()=>{scrollListTop();load();},350);});watch(()=>route.params.id,id=>{if(id)detail(id);else {detailGeneration++;stopDetail();selected.value=null;}observeFeed();});
onMounted(()=>{load();clockTimer=setInterval(()=>clock.value=Date.now(),60000);window.addEventListener('resize',observeFeed);});onBeforeUnmount(()=>{feedObserver?.disconnect();window.removeEventListener('resize',observeFeed);detailGeneration++;stopDetail();clearTimeout(debounce);clearInterval(clockTimer);});
</script>

<template>
  <div class="site-shell" :class="{'article-open':route.params.id}">
    <div class="ambient-background" aria-hidden="true"><span class="ambient-orb"></span></div>
    <header class="topbar liquid-glass">
      <div class="brand"><img class="brand-mark" src="/cdnews.svg" alt="" width="36" height="36" /><div><strong>CDNews</strong><small>全球双语新闻<span class="brand-edition"> · GLOBAL EDITION</span></small></div></div>
      <div class="global-modes" aria-label="全局阅读模式"><button v-for="m in [{id:'zh',label:'中文'},{id:'bilingual',label:'双语'},{id:'en',label:'EN'}]" :key="m.id" :class="{active:mode===m.id}" :aria-pressed="mode===m.id" @click="mode=m.id">{{m.label}}</button></div><nav class="category-nav" aria-label="领域分类"><button v-for="item in categories" :key="item" :class="{ active: category === item }" :aria-pressed="category===item" @click="selectCategory(item)">{{ item }}</button></nav>
      <div class="top-actions"><label class="search"><svg class="search-icon" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true"><circle cx="8.5" cy="8.5" r="5.5"/><path d="m13 13 4 4"/></svg><input v-model="query" placeholder="搜索报道" aria-label="搜索报道" /></label><button class="mode-mini" @click="mode = mode === 'bilingual' ? 'zh' : mode === 'zh' ? 'en' : 'bilingual'">{{ mode === "bilingual" ? "中英" : mode === "zh" ? "中文" : "EN" }}</button></div>
    </header>
    <main class="workspace">
      <aside ref="feedPanel" class="feed-panel liquid-glass">
        <div class="feed-heading"><h1>今日简报 <span class="count">({{ total }})</span></h1><select v-model="source" aria-label="按来源筛选"><option v-for="item in sources" :key="item">{{ item }}</option></select></div>
        <p v-if="error" class="feed-error" role="alert">{{error}} <button @click="load()">重试</button></p><p v-if="loading" class="feed-state" role="status">正在读取报道…</p><div class="feed-list">
          <div v-for="article in filtered" :key="article.id" class="story-group" :class="{selected:selected?.id===article.id||article.folded.some(item=>item.id===selected?.id)}">
            <button class="story-card" :aria-current="selected?.id===article.id?'true':undefined" @click="selectArticle(article.id)">
              <div class="story-meta"><span class="source-mark">{{ article.source_mark }}</span><span class="story-source">{{ article.source_name }}</span><span class="category-tag">{{ article.category }}</span><span v-if="article.lang==='zh'" class="original-language">中文原稿</span><time :datetime="article.published_at" :title="exactDate(article.published_at)">{{ dateLabel(article.published_at) }}</time></div>
              <p v-if="article.lang!=='zh'&&mode==='bilingual'&&article.title_zh&&article.title_zh!==article.title_en" class="story-en" lang="en">{{ article.title_en }}</p>
              <h2 :class="{'title-zh':mode!=='en'}">{{ mode==='en'?article.title_en:(article.title_zh||article.title_en) }}</h2>
              <p v-if="mode!=='en'&&article.summary_zh.length" class="story-summary">{{ article.summary_zh[0] }}</p>
            </button>
            <div v-if="article.folded.length" class="folded-sources"><span>同题报道</span><button v-for="item in article.folded" :key="item.id" :aria-current="selected?.id===item.id?'true':undefined" @click="selectArticle(item.id)">{{ item.source_name }} · {{ mode==='en'?item.title_en:(item.title_zh||item.title_en) }} ↗</button></div>
          </div>
          <div v-if="!loading && !error && !filtered.length" class="empty-state"><strong>暂时没有可阅读的报道</strong><span>试试其他分类或关键词。</span></div><div ref="loadSentinel" class="feed-load-status" role="status" aria-live="polite"><template v-if="loadingMore">正在加载更早的报道…</template><template v-else-if="moreError">加载失败，已保留当前列表。<button type="button" @click="retryMore">重试</button></template><template v-else-if="!loading&&articles.length">{{hasMore?'上滑继续阅读':'已浏览全部报道'}}</template></div>
        </div>
      </aside>
      <article v-if="selected" ref="readerPanel" class="reader-panel liquid-glass">
        <div class="reader-toolbar"><span class="eyebrow">{{ selected.category_en }} / {{ selected.source_name }}</span><div class="reading-tools"><a :href="selected.origin_url" target="_blank" rel="noopener noreferrer">查看原文</a><button :disabled="fontSize<=15" aria-label="缩小正文字号" @click="fontSize=Math.max(15,fontSize-1)">A−</button><button :disabled="fontSize>=22" aria-label="放大正文字号" @click="fontSize=Math.min(22,fontSize+1)">A+</button></div></div>
        <div class="reader-content"><div class="article-kicker"><span class="source-mark large">{{ selected.source_mark }}</span><span>{{ selected.source_name }}</span><template v-if="selected.author"><span class="dot">·</span><span>{{ selected.author }}</span></template><span class="dot">·</span><time :datetime="selected.published_at" :title="exactDate(selected.published_at)">{{ dateLabel(selected.published_at) }}</time></div><p v-if="selected.lang!=='zh' && effectiveMode==='bilingual' && selected.title_zh && selected.title_zh!==selected.title_en" class="headline-en" lang="en" data-selectable-original>{{ selected.title_en }}</p><h1 :data-selectable-original="effectiveMode==='en'?'':undefined" :class="{'title-zh':effectiveMode!=='en'}">{{ effectiveMode==='en' ? selected.title_en : (selected.title_zh || selected.title_en) }}</h1>
          <section v-if="effectiveMode!=='en'&&selected.summary_zh.length" class="takeaways" aria-label="核心导读"><h2>核心导读</h2><ul><li v-for="point in selected.summary_zh.slice(0,3)" :key="point">{{ point }}</li></ul></section>
          <ArticleCover v-if="selected.cover_image||selected.images?.length" :src="selected.cover_image||''" :images="selected.images||[]" :alt="selected.title_en" :source-mark="selected.source_mark" />
          <p v-if="selected.lang==='zh'" class="original-language-note">中文原稿</p>
          <div v-if="selected.translation_status!=='ready'" class="body-translation-status" role="status">
            <span>{{bodyError||(selected.translation_status==='failed'?'翻译暂不可用':selected.translation_stage==='retrying'?'稍后自动重试':selected.translation_stage==='processing'?'正在翻译':'排队中')}}<template v-if="selected.translation_total"> · {{selected.translation_completed||0}}/{{selected.translation_total}} 段</template></span>
            <button v-if="bodyError||selected.translation_status==='failed'" type="button" @click="detail(selected.id)">重试翻译</button>
          </div>
          <div class="paragraphs" :style="{'--reading-size':fontSize+'px'}"><div v-for="(paragraph, index) in selected.content_en" :key="index" class="paragraph-pair" tabindex="0"><p v-if="effectiveMode !== 'zh'||!selected.content_zh[index]" class="en-paragraph" data-selectable-original lang="en">{{ paragraph }}</p><p v-if="effectiveMode !== 'en'&&selected.content_zh[index]" class="zh-paragraph">{{ selected.content_zh[index] }}</p></div></div>
          <footer v-if="selected.related_sources.length" class="article-footer"><div><span class="eyebrow">相关报道</span><div class="related"><a v-for="item in selected.related_sources" :key="item.origin_url" :href="item.origin_url" target="_blank" rel="noopener noreferrer">{{ item.source_name }} ↗</a></div></div></footer>
        </div>
        <SelectionTranslator v-if="selected.lang!=='zh'" :article-id="selected.id" />
      </article><section v-else class="reader-panel reader-empty liquid-glass"><span class="eyebrow">CDNews · GLOBAL EDITION</span><h2>选择一篇报道，开始阅读</h2></section>
    </main>
    <BackToTop :target="feedPanel" label="新闻列表回到顶部" />
    <BackToTop :target="readerPanel" label="正文回到顶部" />
    <BackToTop viewport :reset-key="route.fullPath" />
  </div>
</template>
