<script setup>
import {ref,onMounted,onBeforeUnmount,computed} from 'vue';
import {api,jsonBody,notify} from '../api';
const emit=defineEmits(['updated']);
const saved=ref(null),error=ref(''),busy=ref(false),form=ref({repository:'NiuBi6666/CodeDog',workflow:'news-crawl.yml',api_url:'https://codedog.online',github_token:''});
const running=computed(()=>['queued','processing'].includes(saved.value?.setup_state));
const labels={unconfigured:'未配置',saved:'待配置',queued:'等待配置',processing:'配置中',ready:'已配置',failed:'配置失败',environment:'已配置'};
let timer;
async function load(initial=false){try{saved.value=await api('/admin/news/collector-settings');if(initial)form.value={repository:saved.value.repository,workflow:saved.value.workflow,api_url:saved.value.api_url,github_token:''};if(running.value&&!timer)timer=setInterval(()=>load(),3000);else if(!running.value&&timer){clearInterval(timer);timer=null;emit('updated');}}catch(e){error.value=e.message;if(timer){clearInterval(timer);timer=null;}}}
async function provision(){busy.value=true;error.value='';try{saved.value=await api('/admin/news/collector-settings/provision',{method:'POST'});await load();return true;}catch(e){error.value=e.message;return false;}finally{busy.value=false;}}
async function save(){busy.value=true;error.value='';try{saved.value=await api('/admin/news/collector-settings',{method:'PUT',body:jsonBody(form.value)});form.value.github_token='';const started=await provision();notify(started?'已保存，配置中':'已保存，配置未启动');emit('updated');}catch(e){error.value=e.message;}finally{busy.value=false;}}
onMounted(()=>load(true));onBeforeUnmount(()=>clearInterval(timer));
</script>
<template>
  <section class="admin-panel news-section">
    <div class="panel-heading news-panel-heading"><div><h2>GitHub 采集配置</h2></div><button class="button button-quiet button-small" type="button" @click="load()">刷新配置状态</button></div>
    <div class="news-panel-body">

      <div v-if="error" class="notice notice-error" role="alert">{{error}}</div>
      <p v-if="!saved&&!error" class="news-description">正在读取采集配置…</p>
      <template v-if="saved">
        <div class="news-setup-status" role="status"><span class="status-badge" :class="saved.setup_state==='ready'||saved.setup_state==='environment'?'status-normal':saved.setup_state==='failed'?'status-failed':running?'status-warning':'status-offline'">{{labels[saved.setup_state]||saved.setup_state}}</span></div>
        <div v-if="saved.last_error" class="notice notice-error" role="alert">{{saved.last_error}}</div>
        <form class="form-stack news-form" @submit.prevent="save">
          <fieldset :disabled="busy||running" class="news-fieldset"><div class="news-form-grid">
            <label>GitHub 仓库<input v-model.trim="form.repository" required maxlength="200" placeholder="所有者/仓库名"></label>
            <label>采集工作流<input v-model.trim="form.workflow" required maxlength="100" placeholder="news-crawl.yml"></label>
            <label>网站 API 地址<input v-model.trim="form.api_url" type="url" required maxlength="500" placeholder="https://codedog.online"></label>
            <label>GitHub Token<input v-model="form.github_token" type="password" :required="!saved.credential_configured" maxlength="4096" autocomplete="new-password" :spellcheck="false" :placeholder="saved.credential_configured?'已配置，留空不修改':'粘贴新 Token'"></label>
          </div>

          <div v-if="!saved.encryption_available" class="notice notice-error">服务器加密存储不可用，暂不能保存。</div>
          <div class="news-form-footer"><a class="news-inline-link" href="https://github.com/settings/personal-access-tokens" target="_blank" rel="noopener noreferrer">管理 Token ↗</a><div class="button-row"><button v-if="saved.credential_configured" class="button button-quiet" type="button" @click="provision">重试已保存配置</button><button class="button button-primary" type="submit" :disabled="!saved.encryption_available">{{running?'配置进行中…':busy?'保存中…':'保存并自动配置'}}</button></div></div>
          </fieldset>
        </form>
      </template>
    </div>
  </section>
</template>
