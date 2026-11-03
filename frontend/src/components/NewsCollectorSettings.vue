<script setup>
import {ref,onMounted,onBeforeUnmount,computed} from 'vue';
import {api,jsonBody,notify} from '../api';
const emit=defineEmits(['updated']);
const saved=ref(null),error=ref(''),busy=ref(false),form=ref({repository:'NiuBi6666/CodeDog',workflow:'lens-crawl.yml',api_url:'https://codedog.online',github_token:''});
const running=computed(()=>['queued','processing'].includes(saved.value?.setup_state));
const labels={unconfigured:'未配置',saved:'已保存，待自动配置',queued:'等待配置',processing:'正在自动配置',ready:'已配置并触发采集',failed:'配置失败',environment:'使用服务器配置'};
let timer;
async function load(initial=false){try{saved.value=await api('/admin/news/collector-settings');if(initial)form.value={repository:saved.value.repository,workflow:saved.value.workflow,api_url:saved.value.api_url,github_token:''};if(running.value&&!timer)timer=setInterval(()=>load(),3000);else if(!running.value&&timer){clearInterval(timer);timer=null;emit('updated');}}catch(e){error.value=e.message;if(timer){clearInterval(timer);timer=null;}}}
async function provision(){busy.value=true;error.value='';try{saved.value=await api('/admin/news/collector-settings/provision',{method:'POST'});await load();return true;}catch(e){error.value=e.message;return false;}finally{busy.value=false;}}
async function save(){busy.value=true;error.value='';try{saved.value=await api('/admin/news/collector-settings',{method:'PUT',body:jsonBody(form.value)});form.value.github_token='';const started=await provision();notify(started?'采集配置已加密保存，正在自动配置 GitHub':'采集配置已保存，自动配置未启动，请查看错误提示');emit('updated');}catch(e){error.value=e.message;}finally{busy.value=false;}}
onMounted(()=>load(true));onBeforeUnmount(()=>clearInterval(timer));
</script>
<template>
  <section class="admin-panel news-section">
    <div class="panel-heading news-panel-heading"><div><h2>GitHub 采集配置</h2><p class="news-description">填写 Token，自动完成采集配置并启动首次抓取。</p></div><button class="button button-quiet button-small" type="button" @click="load()">刷新配置状态</button></div>
    <div class="news-panel-body">
      <p class="news-hint">Token 仅授权目标仓库，<strong>Actions、Secrets、Variables</strong> 均需设置为 <strong>Read and write</strong>。<a class="news-inline-link" href="https://github.com/settings/personal-access-tokens" target="_blank" rel="noopener noreferrer">管理 GitHub Token ↗</a></p>
      <div v-if="error" class="notice notice-error" role="alert">{{error}}</div>
      <p v-if="!saved&&!error" class="news-description">正在读取采集配置…</p>
      <template v-if="saved">
        <div class="news-setup-status" role="status"><span class="status-badge" :class="saved.setup_state==='ready'||saved.setup_state==='environment'?'status-normal':saved.setup_state==='failed'?'status-failed':running?'status-warning':'status-offline'">{{labels[saved.setup_state]||saved.setup_state}}</span><span v-if="saved.setup_step" class="news-description">{{saved.setup_step}}</span></div>
        <div v-if="saved.last_error" class="notice notice-error" role="alert">{{saved.last_error}}</div>
        <form class="form-stack news-form" @submit.prevent="save">
          <fieldset :disabled="busy||running" class="news-fieldset"><div class="news-form-grid">
            <label>GitHub 仓库<input v-model.trim="form.repository" required maxlength="200" placeholder="所有者/仓库名"></label>
            <label>采集工作流<input v-model.trim="form.workflow" required maxlength="100" placeholder="lens-crawl.yml"></label>
            <label>网站 API 地址<input v-model.trim="form.api_url" type="url" required maxlength="500" placeholder="https://codedog.online"></label>
            <label>GitHub Token<input v-model="form.github_token" type="password" :required="!saved.credential_configured" maxlength="4096" autocomplete="new-password" :spellcheck="false" :placeholder="saved.credential_configured?'已保存；留空保留原 Token':'粘贴新 Token'"><small>加密保存，不回显；更换仓库时须重新输入 Token。</small></label>
          </div>
          <details class="news-details"><summary>自动配置内容</summary><p>生成并同步 Webhook 采集密钥，设置 NEWS_API_URL 和 NEWS_ENABLED，启用并运行 main 分支的采集工作流。</p></details>
          <div v-if="!saved.encryption_available" class="notice notice-error">服务器加密存储不可用，暂不能保存。</div>
          <div class="news-form-footer"><span class="news-description">保存后即时生效；新闻将在抓取和正文翻译完成后显示。</span><div class="button-row"><button v-if="saved.credential_configured" class="button button-quiet" type="button" @click="provision">重试已保存配置</button><button class="button button-primary" type="submit" :disabled="!saved.encryption_available">{{running?'配置进行中…':busy?'保存中…':'保存并自动配置'}}</button></div></div>
          </fieldset>
        </form>
      </template>
    </div>
  </section>
</template>
