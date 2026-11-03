
<script setup>
import { ref,computed,onMounted } from 'vue';
import { Settings,Languages,Rss,History } from '@lucide/vue';
import '../news-admin.css';
import NewsCollectorSettings from '../components/NewsCollectorSettings.vue';
import AdminLayout from '../components/AdminLayout.vue';
import { api,jsonBody,notify } from '../api';
import { auth,hasPermission } from '../auth';
const selectedTab=ref('collector');
const tabs=computed(()=>[
  ...(auth.user?.admin?[{id:'collector',label:'采集配置',icon:Settings},{id:'translation',label:'翻译配置',icon:Languages}]:[]),
  {id:'sources',label:'订阅源',icon:Rss},{id:'history',label:'采集记录',icon:History}
]);
const activeTab=computed(()=>tabs.value.some(tab=>tab.id===selectedTab.value)?selectedTab.value:'sources');
function moveTab(event,index){
  const keys={ArrowRight:1,ArrowLeft:-1,Home:0,End:0};
  if(!(event.key in keys))return;
  event.preventDefault();
  const next=event.key==='Home'?0:event.key==='End'?tabs.value.length-1:(index+keys[event.key]+tabs.value.length)%tabs.value.length;
  selectedTab.value=tabs.value[next].id;
  event.currentTarget.parentElement.querySelectorAll('[role="tab"]')[next].focus();
}
const data=ref(null),error=ref(''),busy=ref(false),editing=ref(null);
const aiSettings=ref(null),aiError=ref(''),aiBusy=ref(false),aiTest=ref('');
const aiForm=ref({mode:'translators',engine:'bing',base_url:'https://api.siliconflow.cn/v1',model:'',api_key:''});
const providers=[{label:'硅基流动',url:'https://api.siliconflow.cn/v1'},{label:'DeepSeek',url:'https://api.deepseek.com/v1'},{label:'智谱',url:'https://open.bigmodel.cn/api/paas/v4'}];
async function loadAi(){if(!auth.user?.admin)return;try{const r=await api('/admin/news/ai-settings');aiSettings.value=r;aiForm.value={mode:r.mode||'translators',engine:r.engine||'bing',base_url:r.base_url||'https://api.siliconflow.cn/v1',model:r.model||'',api_key:''};aiError.value='';}catch(e){aiError.value=e.message;}}
async function saveAi(){aiBusy.value=true;aiError.value='';aiTest.value='';try{const r=await api('/admin/news/ai-settings',{method:'PUT',body:jsonBody(aiForm.value)});aiSettings.value=r;aiForm.value.api_key='';notify('翻译配置已保存并生效');await load();}catch(e){aiError.value=e.message;}finally{aiBusy.value=false;}}
async function testAi(){aiBusy.value=true;aiError.value='';aiTest.value='';try{const r=await api('/admin/news/ai-settings/test',{method:'POST'});aiTest.value=r.message;}catch(e){aiError.value=e.message;}finally{aiBusy.value=false;}}
const empty=()=>({source_name:'',rss_url:'',category:'科技',priority:80,refresh_interval_min:30,is_active:true});
const form=ref(empty());
async function load(){try{data.value=await api('/admin/news');error.value='';}catch(e){error.value=e.message;}}
function edit(s){form.value={...s};editing.value=s.id;}
function cancel(){form.value=empty();editing.value=null;}
async function save(){busy.value=true;try{await api('/admin/sources'+(editing.value?'/'+editing.value:''),{method:editing.value?'PATCH':'POST',body:jsonBody(form.value)});notify('订阅源已保存');cancel();await load();}catch(e){error.value=e.message;}finally{busy.value=false;}}
async function toggle(s){try{await api('/admin/sources/'+s.id,{method:'PATCH',body:jsonBody({is_active:!s.is_active})});await load();}catch(e){error.value=e.message;}}
async function remove(s){if(!window.confirm('删除订阅源“'+s.source_name+'”？已入库文章将保留。'))return;try{await api('/admin/sources/'+s.id,{method:'DELETE'});await load();}catch(e){error.value=e.message;}}
async function refresh(){busy.value=true;try{const r=await api('/admin/news/refresh',{method:'POST'});notify(r.message);await load();}catch(e){error.value=e.message;}finally{busy.value=false;}}
function date(s){return s?new Date(s).toLocaleString('zh-CN'):'尚无记录';}
const labels={pending:'待编译',processing:'编译中',ready:'已发布',failed:'失败',queued:'待接收',done:'已处理',dispatched:'已调度',received:'已接收'};
onMounted(()=>{load();loadAi();});
</script>
<template>
  <AdminLayout page-title="新闻聚合" active-page="news">
    <div class="news-admin">
      <div class="admin-page-heading">
        <div><h1>新闻聚合</h1><p>管理新闻来源、采集配置与正文翻译</p></div>
        <div class="button-row">
          <a class="button button-quiet" href="/news" target="_blank" rel="noopener">打开新闻站 ↗</a>
          <button class="button button-quiet" type="button" @click="load">刷新状态</button>
          <button v-if="hasPermission('news.refresh')" class="button button-primary" type="button" :disabled="busy" @click="refresh">{{busy?'提交中…':'立即采集'}}</button>
        </div>
      </div>
      <div v-if="error" class="notice notice-error" role="alert">{{error}}</div>
      <div v-if="!data&&!error" class="admin-panel panel-empty" role="status">正在读取新闻状态…</div>
      <template v-if="data">
        <section class="admin-panel news-section news-service-status" aria-label="服务配置状态">
          <strong>服务配置</strong>
          <span class="status-badge" :class="data.configuration.ai_configured?'status-normal':'status-warning'">正文翻译 · {{data.configuration.ai_configured?'已配置':'未配置'}}</span>
          <span class="status-badge" :class="data.configuration.github_configured?'status-normal':'status-warning'">GitHub 调度 · {{data.configuration.github_configured?'已配置':'未配置'}}</span>
          <span class="status-badge" :class="data.configuration.webhook_configured?'status-normal':'status-warning'">采集鉴权 · {{data.configuration.webhook_configured?'已配置':'未配置'}}</span>
          <span v-if="data.configuration.model" class="news-service-detail">当前服务：{{data.configuration.model}}</span>
        </section>
        <nav class="ranking-workspace-tabs news-workspace-tabs" role="tablist" aria-label="新闻聚合功能">
          <button v-for="(tab,index) in tabs" :id="'news-tab-'+tab.id" :key="tab.id" type="button" role="tab"
            :class="{active:activeTab===tab.id}" :aria-selected="activeTab===tab.id" :aria-controls="'news-panel-'+tab.id"
            :tabindex="activeTab===tab.id?0:-1" @click="selectedTab=tab.id" @keydown="moveTab($event,index)">
            <component :is="tab.icon" :size="17" aria-hidden="true" />{{tab.label}}
          </button>
        </nav>

        <div v-if="auth.user?.admin" v-show="activeTab==='collector'" id="news-panel-collector" role="tabpanel" aria-labelledby="news-tab-collector" tabindex="0">
          <NewsCollectorSettings @updated="load" />
        </div>

        <section v-if="auth.user?.admin" v-show="activeTab==='translation'" id="news-panel-translation" role="tabpanel" aria-labelledby="news-tab-translation" tabindex="0" class="admin-panel news-section">
          <div class="panel-heading news-panel-heading"><div><h2>正文翻译配置</h2><p class="news-description">采集后翻译标题，打开新闻详情时按需翻译全文；保留英文原文，不生成摘要。</p></div><span class="status-badge status-offline">仅系统管理员</span></div>
          <div class="news-panel-body">
            <div v-if="aiError" class="notice notice-error" role="alert">{{aiError}}</div>
            <div v-if="aiTest" class="notice notice-success" role="status">{{aiTest}}</div>
            <p v-if="!aiSettings&&!aiError" class="news-description">正在读取翻译配置…</p>
            <form v-if="aiSettings" class="form-stack news-form" @submit.prevent="saveAi">
              <div class="news-form-grid">
                <label>翻译方式<select v-model="aiForm.mode"><option value="translators">默认翻译（无需 API Key）</option><option value="api">大模型 API</option></select></label>
                <label v-if="aiForm.mode==='translators'">翻译引擎<select v-model="aiForm.engine"><option value="bing">Bing</option><option value="alibaba">Alibaba</option><option value="baidu">Baidu</option></select></label>
              </div>
              <p v-if="aiForm.mode==='translators'" class="news-hint">默认翻译依赖第三方在线服务，遇到限流或不可用时可切换引擎。切换方式会保留已保存的 API Key。</p>
              <template v-if="aiForm.mode==='api'">
                <div class="button-row news-presets"><span>常用服务</span><button v-for="provider in providers" :key="provider.url" class="button button-quiet button-small" type="button" @click="aiForm.base_url=provider.url;aiForm.api_key='';aiTest=''">{{provider.label}}</button></div>
                <div class="news-form-grid">
                  <label>API Base URL<input v-model.trim="aiForm.base_url" type="url" placeholder="https://api.example.com/v1" maxlength="500" required autocomplete="off"><small>填写接口根地址，不含 /chat/completions。</small></label>
                  <label>模型名称<input v-model.trim="aiForm.model" placeholder="服务商控制台中的完整模型名称" maxlength="200" required autocomplete="off"></label>
                  <label class="news-field-wide">API Key<input v-model="aiForm.api_key" type="password" :required="!aiSettings.api_key_configured" :placeholder="aiSettings.api_key_configured?'已保存；留空保留原密钥':'粘贴服务商 API Key'" maxlength="4096" autocomplete="new-password" :spellcheck="false"><small>{{aiSettings.api_key_configured?'密钥已配置；更换服务地址时须填写对应服务商的新密钥。':'密钥加密保存，不回显。'}}</small></label>
                </div>
              </template>
              <div v-if="aiForm.mode==='api'&&!aiSettings.encryption_available" class="notice notice-error">服务器加密存储暂不可用，请联系管理员。</div>
              <div class="news-form-footer"><span class="news-description">保存后立即生效。测试使用已保存配置，大模型测试可能消耗少量额度。</span><div class="button-row"><button class="button button-quiet" type="button" :disabled="aiBusy||!aiSettings.configured" @click="testAi">测试已保存配置</button><button class="button button-primary" type="submit" :disabled="aiBusy||(aiForm.mode==='api'&&!aiSettings.encryption_available)">{{aiBusy?'处理中…':'保存翻译配置'}}</button></div></div>
            </form>
          </div>
        </section>

        <section v-show="activeTab==='sources'" id="news-panel-sources" role="tabpanel" aria-labelledby="news-tab-sources" tabindex="0" class="admin-panel news-section">
          <div class="panel-heading news-panel-heading"><div><h2>订阅源</h2><p class="news-description">每 30 分钟检查一次，按各来源的刷新间隔采集。</p></div><span class="status-badge status-offline">{{data.sources.length}} 个来源</span></div>
          <div class="document-table-wrap news-table-wrap"><table class="document-table"><thead><tr><th>媒体 / RSS</th><th>分类</th><th>频率 / 权重</th><th>最近抓取</th><th>最近成功</th><th>状态</th><th>操作</th></tr></thead><tbody>
            <tr v-for="s in data.sources" :key="s.id"><td class="news-title-cell"><strong>{{s.source_name}}</strong><small>{{s.rss_url}}</small><span v-if="s.last_error" class="news-row-error">{{s.last_error}}</span></td><td>{{s.category}}</td><td>{{s.refresh_interval_min}} 分钟 / {{s.priority}}</td><td>{{date(s.last_crawled_at)}}</td><td>{{date(s.last_success_at)}}</td><td><span class="status-badge" :class="s.is_active?'status-normal':'status-offline'">{{s.is_active?'已启用':'已暂停'}}</span></td><td><div class="button-row news-table-actions"><button v-if="hasPermission('news.sources.update')" class="button button-quiet button-small" type="button" @click="edit(s)">编辑</button><button v-if="hasPermission('news.sources.update')" class="button button-quiet button-small" type="button" @click="toggle(s)">{{s.is_active?'停用':'启用'}}</button><button v-if="hasPermission('news.sources.delete')" class="button button-quiet button-small news-danger" type="button" @click="remove(s)">删除</button></div></td></tr>
          </tbody></table></div>
          <form v-if="hasPermission(editing?'news.sources.update':'news.sources.create')" class="form-stack news-form news-panel-body news-source-form" @submit.prevent="save">
            <h3>{{editing?'编辑订阅源':'添加订阅源'}}</h3>
            <div class="news-source-fields"><label>媒体名称<input v-model="form.source_name" maxlength="80" required></label><label>HTTPS RSS 地址<input v-model="form.rss_url" type="url" required placeholder="https://example.com/feed"></label><label>领域<select v-model="form.category"><option>科技</option><option>国际</option><option>财经</option><option>教育</option></select></label><label>刷新间隔（分钟）<input v-model.number="form.refresh_interval_min" type="number" min="30" max="1440" required></label><label>媒体权重<input v-model.number="form.priority" type="number" min="0" max="100" required></label></div>
            <div class="news-form-footer"><span class="news-description">刷新间隔为 30–1440 分钟，权重为 0–100。</span><div class="button-row"><button v-if="editing" class="button button-quiet" type="button" @click="cancel">取消</button><button class="button button-primary" type="submit" :disabled="busy">保存来源</button></div></div>
          </form>
        </section>

        <section v-show="activeTab==='history'" id="news-panel-history" role="tabpanel" aria-labelledby="news-tab-history" tabindex="0" class="admin-panel news-section">
          <div class="panel-heading"><h2>采集记录</h2></div>
          <div v-if="!data.runs.length&&!data.batches.length" class="panel-empty">尚无采集记录</div>
          <div v-else class="news-panel-body news-run-list"><div v-for="r in data.runs" :key="'run-'+r.id"><time>{{date(r.created_at)}}</time><span class="status-badge" :class="r.status==='failed'?'status-failed':'status-offline'">{{labels[r.status]||r.status}}</span></div><div v-for="b in data.batches" :key="b.batch_id"><time>{{date(b.created_at)}}</time><span class="status-badge" :class="b.status==='done'?'status-normal':b.status==='failed'?'status-failed':'status-warning'">{{labels[b.status]||b.status}}</span><small>批次 {{b.batch_id}}</small><span v-if="b.last_error" class="news-row-error">{{b.last_error}}</span></div></div>
        </section>
      </template>

    </div>
  </AdminLayout>
</template>
