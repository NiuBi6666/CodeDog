<script setup>
import {computed,ref,watch,nextTick,onMounted,onBeforeUnmount} from 'vue';
import {useRoute,useRouter} from 'vue-router';
import ArticleCover from './ArticleCover.vue';
import SelectionTranslator from './SelectionTranslator.vue';
import NewspaperStory from './NewspaperStory.vue';
import './newspaper.css';
import {leadExcerpt} from './newspaper-layout.js';
const route=useRoute(),router=useRouter();
const categories=['全部','国际','军事','科技','财经','教育','文娱','生活'];
const category=ref('全部'),query=ref(''),mode=ref('bilingual'),articles=ref([]),loading=ref(true),error=ref(''),hasMore=ref(false),cursor=ref(''),loadingMore=ref(false),moreError=ref(''),total=ref(0);
const sheet=ref(null),sheetBody=ref(null),selected=ref(null),detailError=ref(''),translationError=ref(''),preview=ref(null),leadFailed=ref(false),sentinel=ref(null);
const lead=computed(()=>articles.value.slice(0,8).find(a=>a.cover_image)||articles.value[0]);
const remaining=computed(()=>articles.value.filter(a=>a.id!==lead.value?.id));
const side=computed(()=>remaining.value.slice(0,4)),briefs=computed(()=>remaining.value.slice(4,8)),rest=computed(()=>remaining.value.slice(8));
const effectiveMode=computed(()=>selected.value?.lang==='zh'?'zh':mode.value);
const paragraphs=v=>Array.isArray(v)?v:(v||'').split(/\n\s*\n/).filter(Boolean);
const originals=computed(()=>paragraphs(selected.value?.content_original||selected.value?.content_en));
const translations=computed(()=>paragraphs(selected.value?.paragraphs_zh||selected.value?.content_zh));
const date=ref(new Date());
const edition=computed(()=>date.value.toLocaleDateString('zh-CN',{year:'numeric',month:'long',day:'numeric',weekday:'long'}));
const leadParagraphs=computed(()=>leadExcerpt(preview.value));
const leadMode=computed(()=>lead.value?.lang==='zh'?'zh':mode.value);
let listTicket=0,detailTicket=0,previewTicket=0,debounce,poll,refreshTimer,observer,returnFocus,scrollY=0,priorOverflow='',disposed=false;
async function api(path,options={}){
 const c=new AbortController(),timeout=setTimeout(()=>c.abort(),20000);
 try{const r=await fetch('/api'+path,{...options,signal:c.signal});const data=await r.json();if(!r.ok)throw Error(data.error||'请求失败');return data;}
 catch(e){throw Error(e.name==='AbortError'?'请求超时，请重试':e.message);}finally{clearTimeout(timeout);}
}
function listPath(after=''){return '/news?'+new URLSearchParams({category:category.value==='全部'?'':category.value,q:query.value,...(after?{cursor:after}:{})});}
async function observe(){observer?.disconnect();await nextTick();if(disposed||!sentinel.value||!hasMore.value||loading.value||moreError.value||sheet.value?.open)return;observer=new IntersectionObserver(es=>{if(es.some(e=>e.isIntersecting))more();},{rootMargin:'240px'});observer.observe(sentinel.value);}
async function load(silent=false){
 const ticket=++listTicket;if(!silent){loading.value=true;error.value='';}
 try{const data=await api(listPath());if(disposed||ticket!==listTicket)return;
 const incoming=data.articles||[];
 if(silent){const ids=new Set(articles.value.map(a=>a.id));const fresh=incoming.filter(a=>!ids.has(a.id));if(fresh.length)articles.value=[...fresh,...articles.value].sort((a,b)=>new Date(b.published_at)-new Date(a.published_at)||b.id-a.id);}
 else {articles.value=incoming;hasMore.value=data.has_more;cursor.value=data.next_cursor||'';moreError.value='';}
 total.value=data.total||0;
 }catch(e){if(ticket===listTicket&&!silent)error.value=e.message;}
 finally{if(ticket===listTicket){loading.value=false;observe();}}
}
async function more(){
 if(loadingMore.value||loading.value||!hasMore.value||!cursor.value||moreError.value)return;
 const ticket=listTicket;loadingMore.value=true;
 try{const data=await api(listPath(cursor.value));if(ticket!==listTicket||disposed)return;const ids=new Set(articles.value.map(a=>a.id));articles.value.push(...data.articles.filter(a=>!ids.has(a.id)));cursor.value=data.next_cursor||'';hasMore.value=data.has_more;}
 catch(e){if(ticket===listTicket)moreError.value=e.message;}
 finally{loadingMore.value=false;observe();}
}
function open(id){router.push({query:{...route.query,article:String(id)}});}
function close(){if(route.query.article){const q={...route.query};delete q.article;router.replace({query:q});}}
function finishClose(){detailTicket++;clearTimeout(poll);if(sheet.value?.open)sheet.value.close();document.documentElement.style.overflow=priorOverflow;window.scrollTo({top:scrollY,behavior:'instant'});selected.value=null;returnFocus?.focus({preventScroll:true});document.title='CDNews · 视界日报';observe();}
async function read(id,retry=false){
 const ticket=++detailTicket;clearTimeout(poll);detailError.value='';translationError.value='';
 if(!sheet.value?.open){returnFocus=document.activeElement;scrollY=window.scrollY;priorOverflow=document.documentElement.style.overflow;sheet.value.showModal();document.documentElement.style.overflow='hidden';}
 if(!retry){selected.value=null;sheetBody.value.scrollTop=0;}
 try{
 const a=await api('/news/'+id);if(ticket!==detailTicket||disposed)return;selected.value=a;document.title=(a.title_zh||a.title_en)+' · CDNews';
 if(a.lang==='zh'||a.translation_status==='ready')return;
 const csrf=await api('/auth/csrf');if(ticket!==detailTicket)return;
 await api('/news/'+id+'/translation',{method:'POST',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':csrf.token},body:'{}'});
 const started=Date.now();
 async function tick(){if(ticket!==detailTicket||disposed)return;try{const a=await api('/news/'+id);if(ticket!==detailTicket)return;selected.value=a;if(['ready','failed'].includes(a.translation_status))return;translationError.value='';}catch(e){if(ticket!==detailTicket)return;translationError.value='连接恢复中';}
 if(Date.now()-started<600000)poll=setTimeout(tick,2000);else translationError.value='仍在后台翻译';}
 if(ticket===detailTicket)poll=setTimeout(tick,1000);
 }catch(e){if(ticket===detailTicket){if(selected.value)translationError.value=e.message;else detailError.value=e.message;}}
}
function trap(event){if(event.key!=='Tab'||event.target.closest('dialog')!==sheet.value)return;const focusable=[...sheet.value.querySelectorAll('button:not(:disabled),a[href],input,select,[tabindex="0"]')].filter(e=>e.offsetParent!==null);const first=focusable[0],last=focusable.at(-1);if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}}
watch(()=>route.query.article,id=>{if(id&&/^\d+$/.test(String(id)))read(id);else if(sheet.value?.open)finishClose();});
watch([category,query],()=>{clearTimeout(debounce);debounce=setTimeout(()=>load(),query.value?300:0);});
watch(()=>lead.value?.id,async id=>{preview.value=null;leadFailed.value=false;const t=++previewTicket;if(!id)return;try{const a=await api('/news/'+id);if(t===previewTicket&&!disposed)preview.value=a;}catch{}});
onMounted(()=>{document.title='CDNews · 视界日报';load();if(route.query.article)read(route.query.article);refreshTimer=setInterval(()=>{date.value=new Date();if(!document.hidden&&!sheet.value?.open&&!query.value)load(true);},60000);});
onBeforeUnmount(()=>{disposed=true;listTicket++;detailTicket++;previewTicket++;clearTimeout(debounce);clearTimeout(poll);clearInterval(refreshTimer);observer?.disconnect();if(sheet.value?.open){sheet.value.close();document.documentElement.style.overflow=priorOverflow;}});
</script>
<template>
 <div class="press-page">
  <main class="press-paper">
   <header class="press-masthead">
    <div class="press-edition"><span>CDNEWS · GLOBAL EDITION</span><time>{{edition}}</time><a href="/news/">双语阅读版 ↗</a></div>
    <div class="press-name"><span class="press-seal" aria-hidden="true">世<br>界</span><div><p>THE WORLD, IN PRINT</p><h1>视界日报<span>CDNEWS</span></h1></div><span class="press-seal" aria-hidden="true">今<br>日</span></div>
    <div class="press-motto"><span>记录世界 · 见闻有据</span><span>{{total}} 篇报道 · 全球新闻</span></div>
   </header>
   <div class="press-navigation">
    <nav aria-label="新闻分类"><button v-for="c in categories" :key="c" :aria-pressed="category===c" :class="{active:category===c}" @click="category=c">{{c}}</button></nav>
    <div class="press-options"><div class="press-languages" aria-label="阅读语言"><button v-for="m in [{id:'zh',name:'中文'},{id:'bilingual',name:'双语'},{id:'en',name:'EN'}]" :key="m.id" :aria-pressed="mode===m.id" @click="mode=m.id">{{m.name}}</button></div><label class="press-search"><span class="sr-only">搜索报道</span><input v-model="query" aria-label="搜索报道" placeholder="搜索报道"><svg viewBox="0 0 20 20" width="15" height="15" fill="none" stroke="currentColor" aria-hidden="true"><circle cx="8" cy="8" r="5"/><path d="m12 12 5 5"/></svg></label></div>
   </div>
   <p v-if="loading" class="press-state" role="status">正在读取报道…</p>
   <p v-else-if="error" class="press-state" role="alert">{{error}} <button @click="load()">重试</button></p>
   <p v-else-if="!articles.length" class="press-state">暂无报道</p>
   <template v-else-if="lead">
    <section class="press-front" aria-label="今日头版">
     <article class="press-lead">
      <div class="press-section-label"><span>头版 / {{category==='全部'?'全球要闻':category}}</span><span>{{lead.source_name}}</span></div>
      <a :href="'/newspaper?article='+lead.id" @click.prevent="open(lead.id)" class="press-lead-link">
       <p v-if="mode==='bilingual'&&lead.lang!=='zh'&&lead.title_zh&&lead.title_zh!==lead.title_en" class="press-lead-original" lang="en">{{lead.title_en}}</p>
       <h2>{{mode==='en'?(lead.title_original||lead.title_en):(lead.title_zh||lead.title_en)}}</h2>
      </a>
      <div class="press-lead-meta"><time :datetime="lead.published_at">{{new Date(lead.published_at).toLocaleString('zh-CN',{month:'long',day:'numeric',hour:'2-digit',minute:'2-digit'})}}</time><span v-if="preview?.author">{{preview.author}}</span></div>
      <div class="press-lead-body" :class="{'has-copy':leadParagraphs.length}">
       <figure v-if="lead.cover_image" class="press-lead-figure">
        <a :href="'/newspaper?article='+lead.id" @click.prevent="open(lead.id)" class="press-lead-photo"><span v-if="leadFailed" class="press-photo-fallback">{{lead.source_name}} · 图片暂不可用</span><img v-else :src="'https://wsrv.nl/?url='+encodeURIComponent(lead.cover_image)" :alt="lead.title_zh||lead.title_en" @error="leadFailed=true"></a>
        <figcaption>{{lead.source_name}}</figcaption>
       </figure>
       <div v-if="leadParagraphs.length" class="press-lead-excerpt">
        <div v-for="(p,i) in leadParagraphs" :key="i" class="press-excerpt-pair">
         <p v-if="leadMode!=='zh'||!p.translation" :lang="lead.lang==='zh'?'zh-CN':'en'" :class="{'press-excerpt-original':lead.lang!=='zh'}">{{p.original}}</p>
         <p v-if="leadMode!=='en'&&p.translation" lang="zh-CN" :class="{'press-dropcap':i===0}">{{p.translation}}</p>
        </div>
       </div>
      </div>
      <a :href="'/newspaper?article='+lead.id" class="press-read" @click.prevent="open(lead.id)">阅读全文 <span aria-hidden="true">→</span></a>
     </article>
     <aside class="press-side"><h2 class="press-column-title">新闻纵览 <small>THE DISPATCH</small></h2><NewspaperStory v-for="a in side" :key="a.id" :article="a" :mode="mode" @open="open"/></aside>
     <aside class="press-briefs"><h2 class="press-column-title">即时快讯 <small>IN BRIEF</small></h2><NewspaperStory v-for="a in briefs" :key="a.id" :article="a" :mode="mode" @open="open"/></aside>
    </section>
    <section v-if="rest.length" class="press-lower" aria-label="更多报道"><div class="press-lower-title"><h2>世界，此刻</h2><span>ACROSS THE GLOBE</span></div><div class="press-news-grid"><NewspaperStory v-for="a in rest" :key="a.id" :article="a" :mode="mode" image @open="open"/></div></section>
    <div ref="sentinel" class="press-state" role="status"><template v-if="loadingMore">正在加载…</template><template v-else-if="moreError">{{moreError}} <button @click="moreError='';more()">重试</button></template><span v-else-if="!hasMore">本版完</span></div>
   </template>
   <footer class="press-footer"><span>CDNEWS · 视界日报</span><span>全球视野 / {{category==='全部'?'全领域':category}}</span><span>01</span></footer>
  </main>
  <dialog ref="sheet" class="press-dialog" aria-labelledby="press-article-title" @cancel.prevent="close" @click="e=>{if(e.target===sheet)close()}" @keydown="trap">
   <div ref="sheetBody" class="press-sheet">
    <header class="press-sheet-bar"><span>CDNEWS / {{selected?.category||'报道'}}</span><div><a v-if="selected?.origin_url" :href="selected.origin_url" target="_blank" rel="noopener noreferrer">查看原文</a><button type="button" aria-label="关闭报道" autofocus @click="close">×</button></div></header>
    <p v-if="detailError" class="press-state" role="alert">{{detailError}} <button @click="read(route.query.article)">重试</button></p>
    <p v-else-if="!selected" class="press-state" role="status">正在读取报道…</p>
    <article v-else class="press-article">
     <div class="press-article-byline">{{selected.source_name}}<template v-if="selected.author"> · {{selected.author}}</template> · {{new Date(selected.published_at).toLocaleString('zh-CN',{year:'numeric',month:'long',day:'numeric',hour:'2-digit',minute:'2-digit'})}}</div>
     <p v-if="effectiveMode==='bilingual'&&selected.title_zh&&selected.title_zh!==selected.title_en" class="press-article-original" data-selectable-original lang="en">{{selected.title_original||selected.title_en}}</p>
     <h1 id="press-article-title" :data-selectable-original="effectiveMode==='en'?'':undefined">{{effectiveMode==='en'?(selected.title_original||selected.title_en):(selected.title_zh||selected.title_en)}}</h1>
     <ArticleCover :src="selected.cover_image||''" :images="selected.images||[]" :alt="selected.title_zh||selected.title_en" :source-mark="selected.source_name?.slice(0,1)"/>
     <div v-if="selected.lang!=='zh'&&selected.translation_status!=='ready'" class="press-translation-state" role="status">{{translationError||(selected.translation_status==='failed'?'翻译暂不可用':'正在翻译')}}<span v-if="selected.translation_total"> · {{selected.translation_completed||0}}/{{selected.translation_total}} 段</span><button v-if="translationError||selected.translation_status==='failed'" @click="read(selected.id,true)">重试</button></div>
     <div class="press-article-columns"><div v-for="(p,i) in originals" :key="i" class="press-paragraph"><p v-if="effectiveMode!=='zh'||!translations[i]" lang="en" data-selectable-original>{{p}}</p><p v-if="effectiveMode!=='en'&&translations[i]" lang="zh-CN">{{translations[i]}}</p></div></div>
    </article>
    <SelectionTranslator v-if="selected&&selected.lang!=='zh'" :article-id="selected.id" :teleport-target="sheetBody"/>
   </div>
  </dialog>
 </div>
</template>
