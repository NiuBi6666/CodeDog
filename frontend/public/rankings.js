const elements={camp:document.querySelector("#campSelect"),class:document.querySelector("#classSelect"),classField:document.querySelector("#classField"),status:document.querySelector("#status"),podium:document.querySelector("#podium"),wall:document.querySelector("#nameWall"),subtitle:document.querySelector("#boardSubtitle"),updated:document.querySelector("#lastUpdated")};
const state={catalog:[],scope:new URLSearchParams(location.search).get("scope")==="camp"?"camp":"class",timer:null};
const escapeHtml=value=>String(value??"").replace(/[&<>"']/g,char=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"})[char]);

async function request(path,options={}){const response=await fetch(path,{credentials:"same-origin",...options});const type=response.headers.get("content-type")||"";const data=type.includes("json")?await response.json():null;if(!response.ok)throw new Error(data?.error||"请求失败");return data;}
function option(value,label){return `<option value="${escapeHtml(value)}">${escapeHtml(label)}</option>`;}
function updateQuery(){const params=new URLSearchParams();if(elements.camp.value)params.set("camp",elements.camp.value);if(state.scope==="class"&&elements.class.value)params.set("class",elements.class.value);params.set("scope",state.scope);history.replaceState(null,"",`${location.pathname}?${params}`);}
function selectedCamp(){return state.catalog.find(item=>item.id===elements.camp.value);}
function populateClasses(preferred=""){const classes=selectedCamp()?.classes||[];elements.class.innerHTML=classes.map(item=>option(item.id,item.name)).join("");if(classes.some(item=>item.id===preferred))elements.class.value=preferred;elements.classField.hidden=state.scope==="camp";}

async function loadCatalog(){
  const result=await request("/api/public/rankings/catalog");state.catalog=result.camps||[];
  if(!state.catalog.length){showStatus("暂无排行榜数据");return;}
  const params=new URLSearchParams(location.search),preferredCamp=params.get("camp")||"",preferredClass=params.get("class")||"";
  elements.camp.innerHTML=state.catalog.map(item=>option(item.id,item.name)).join("");
  if(state.catalog.some(item=>item.id===preferredCamp))elements.camp.value=preferredCamp;
  populateClasses(preferredClass);setScope(state.scope,false);await loadBoard();
}

async function loadBoard(){
  if(!elements.camp.value||state.scope==="class"&&!elements.class.value){showStatus("暂无可展示的班级");return;}
  elements.status.classList.remove("error");showStatus("正在更新排行榜…");updateQuery();
  try{
    const params=new URLSearchParams({campId:elements.camp.value,scope:state.scope});if(state.scope==="class")params.set("classId",elements.class.value);
    const board=await request(`/api/public/rankings?${params}`);renderBoard(board);
  }catch(error){elements.status.classList.add("error");showStatus(error.message||"排行榜加载失败");}
}

function renderBoard(board){
  elements.subtitle.textContent=`${board.campName} · ${board.scope==="camp"?"训练营榜":board.className}`;
  elements.updated.textContent=board.updatedAt?`更新于 ${new Intl.DateTimeFormat("zh-CN",{timeZone:"Asia/Shanghai",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hour12:false}).format(new Date(board.updatedAt))}`:"尚未同步";
  const rows=board.rankings||[];if(!rows.length){showStatus("当前范围暂无学员积分");return;}
  elements.status.hidden=true;elements.podium.hidden=false;elements.wall.hidden=false;
  elements.podium.innerHTML=rows.slice(0,3).map(podiumCard).join("");elements.wall.innerHTML=rows.slice(3).map(nameTile).join("");
  if(rows.length<4)elements.wall.hidden=true;
}
function podiumCard(row){return `<article class="podium-card level-${row.level}" data-rank="${row.rank}"><span class="rank-number">#${row.rank}</span><div class="student-name">${escapeHtml(row.studentName)}</div><div class="score">${row.totalPoints} 积分</div><span class="level-label">L${row.level} ${escapeHtml(row.levelName)}</span></article>`;}
function nameTile(row){const band=row.rank<=10?"rank-4-10":row.rank<=30?"rank-11-30":"";return `<article class="name-tile ${band} level-${row.level}"><span class="rank-number">${row.rank}</span><div><div class="student-name">${escapeHtml(row.studentName)}</div><div class="meta">${row.totalPoints} 积分 · L${row.level} ${escapeHtml(row.levelName)}${state.scope==="camp"?` · ${escapeHtml(row.className)}`:""}</div></div></article>`;}
function showStatus(message){elements.status.hidden=false;elements.status.textContent=message;elements.podium.hidden=true;elements.wall.hidden=true;}
function setScope(scope,reload=true){state.scope=scope;document.querySelectorAll("[data-scope]").forEach(button=>button.classList.toggle("active",button.dataset.scope===scope));elements.classField.hidden=scope==="camp";if(reload)loadBoard();}

elements.camp.addEventListener("change",()=>{populateClasses();loadBoard();});elements.class.addEventListener("change",loadBoard);
document.querySelectorAll("[data-scope]").forEach(button=>button.addEventListener("click",()=>setScope(button.dataset.scope)));
document.querySelector("#refreshButton").addEventListener("click",loadBoard);
document.querySelector("#fullscreenButton").addEventListener("click",async()=>{if(document.fullscreenElement)await document.exitFullscreen();else await document.documentElement.requestFullscreen();});
document.addEventListener("fullscreenchange",()=>document.body.classList.toggle("is-fullscreen",Boolean(document.fullscreenElement)));

let csrfToken="";
async function csrf(){if(!csrfToken)csrfToken=(await request("/api/auth/csrf")).token;return csrfToken;}
async function adminRequest(path,options={}){const headers=new Headers(options.headers||{});headers.set("X-XSRF-TOKEN",await csrf());return request(path,{...options,headers});}
async function enableAdmin(){try{await request("/api/auth/me");document.querySelector("#adminPanel").hidden=false;}catch(_error){return;}}
document.querySelector("#pairButton").addEventListener("click",async()=>{const output=document.querySelector("#pairingCode");output.textContent="生成中…";try{const result=await adminRequest("/api/rankings/admin/pairing-codes",{method:"POST"});output.textContent=result.code;}catch(error){output.textContent=error.message;}});
document.querySelector("#importForm").addEventListener("submit",async event=>{event.preventDefault();const output=document.querySelector("#importResult"),button=event.currentTarget.querySelector("button");button.disabled=true;output.textContent="正在导入…";try{const result=await adminRequest("/api/rankings/admin/imports/xlsx",{method:"POST",body:new FormData(event.currentTarget)});output.textContent=`已处理 ${result.receivedRows} 行，更新 ${result.changedRows} 行，拒绝 ${result.rejectedRows} 行`;await loadCatalog();}catch(error){output.textContent=error.message;}finally{button.disabled=false;}});

loadCatalog().catch(error=>{elements.status.classList.add("error");showStatus(error.message||"排行榜加载失败");});enableAdmin();state.timer=setInterval(loadBoard,60_000);
