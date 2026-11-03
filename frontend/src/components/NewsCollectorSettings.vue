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
<template><section class="collector-panel"><h2>GitHub 采集配置</h2><p>填写 Token 后，系统自动生成并同步采集密钥、设置 GitHub 变量、启用工作流并触发首次采集。保存后即时生效，无需编辑服务器文件。</p>
<p class="permissions">Token 仅授权 CodeDog 仓库，并将 <strong>Actions、Secrets、Variables</strong> 三项均设为 <strong>Read and write</strong>。只有 Actions 权限无法自动配置。</p>
<a href="https://github.com/settings/personal-access-tokens" target="_blank" rel="noopener noreferrer">管理 GitHub Token ↗</a>
<p v-if="error" class="error" role="alert">{{error}}</p><p v-if="!saved&&!error">正在读取采集配置…</p>
<template v-if="saved"><p class="status" role="status">{{labels[saved.setup_state]||saved.setup_state}}<span v-if="saved.setup_step"> · {{saved.setup_step}}</span></p><p v-if="saved.last_error" class="error" role="alert">{{saved.last_error}}</p>
<form @submit.prevent="save"><fieldset :disabled="busy||running"><div class="fields"><label>GitHub 仓库<input v-model.trim="form.repository" required maxlength="200" placeholder="所有者/仓库名"></label><label>采集工作流<input v-model.trim="form.workflow" required maxlength="100" placeholder="lens-crawl.yml"></label><label>网站 API 地址<input v-model.trim="form.api_url" type="url" required maxlength="500" placeholder="https://codedog.online"></label><label>GitHub Token<input v-model="form.github_token" type="password" :required="!saved.credential_configured" maxlength="4096" autocomplete="new-password" :spellcheck="false" :placeholder="saved.credential_configured?'已保存；留空保留原 Token':'粘贴新 Token'"><small>Token 加密保存，不回显；更换仓库时须重新输入 Token。</small></label></div>
<p>将写入仓库 Secret：NEWS_WEBHOOK_SECRET，以及变量 NEWS_API_URL、NEWS_ENABLED，并启动 main 分支的采集工作流。新闻在抓取和翻译完成后显示。</p>
<p v-if="!saved.encryption_available" class="error">服务器加密存储不可用，暂不能保存。</p><button type="submit" :disabled="!saved.encryption_available">{{running?'配置进行中…':'保存并自动配置'}}</button><button v-if="saved.credential_configured" type="button" @click="provision">重试已保存配置</button></fieldset></form><button type="button" @click="load()">刷新配置状态</button></template></section></template>
<style scoped>
.collector-panel{padding:22px;background:#fff;border:1px solid #dde4e4;margin:18px 0;color:#26343d}.collector-panel h2{margin:0;font-size:20px}.collector-panel p{font-size:13px;color:#617780;line-height:1.8}.permissions{background:#f8f6ef;padding:12px}.fields{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin:16px 0}label{font-size:12px;color:#526b77}input{display:block;width:100%;padding:10px;border:1px solid #cedbde;margin:7px 0;border-radius:3px;box-sizing:border-box}small{display:block;line-height:1.7;color:#657b84}button,a{display:inline-block;padding:8px 12px;border:1px solid #c7d2d4;background:#fff;border-radius:4px;color:#314d5b;font-size:12px;margin:3px;text-decoration:none;cursor:pointer}fieldset{border:0;padding:0;margin:0;min-width:0}button:disabled,fieldset:disabled{opacity:.6}.error{padding:12px;background:#fff0eb;color:#963622!important}.status{padding:10px;background:#edf5f7}input:focus-visible,button:focus-visible{outline:2px solid #537b8d;outline-offset:2px}@media(max-width:800px){.fields{grid-template-columns:1fr}.collector-panel{padding:14px}}
</style>
