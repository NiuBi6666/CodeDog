import{avatarText,pointsToPass,trendView}from"/ranking-utils.js";

const elements={camp:document.querySelector("#campSelect"),class:document.querySelector("#classSelect"),classField:document.querySelector("#classField"),status:document.querySelector("#status"),podium:document.querySelector("#podium"),wall:document.querySelector("#nameWall"),subtitle:document.querySelector("#boardSubtitle"),updated:document.querySelector("#lastUpdated"),popover:document.querySelector("#scorePopover"),myBar:document.querySelector("#myRankBar"),mySelect:document.querySelector("#myStudentSelect"),myContent:document.querySelector("#myRankContent")};
const query=new URLSearchParams(location.search),state={catalog:[],scope:query.get("scope")==="camp"?"camp":"class",timer:null,rows:[],myStudentId:query.get("student")||"",pinnedId:null};
const escapeHtml=value=>String(value??"").replace(/[&<>"']/g,char=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"})[char]);

async function request(path,options={}){const response=await fetch(path,{credentials:"same-origin",...options});const type=response.headers.get("content-type")||"";const data=type.includes("json")?await response.json():null;if(!response.ok)throw new Error(data?.error||"请求失败");return data;}
function option(value,label){return `<option value="${escapeHtml(value)}">${escapeHtml(label)}</option>`;}
function updateQuery(){const params=new URLSearchParams();if(elements.camp.value)params.set("camp",elements.camp.value);if(state.scope==="class"&&elements.class.value)params.set("class",elements.class.value);if(state.myStudentId)params.set("student",state.myStudentId);params.set("scope",state.scope);history.replaceState(null,"",`${location.pathname}?${params}`);}
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
  const rows=board.rankings||[];state.rows=rows;if(!rows.length){showStatus("当前范围暂无学员积分");elements.myBar.hidden=true;return;}
  elements.status.hidden=true;elements.podium.hidden=false;elements.wall.hidden=false;
  elements.podium.innerHTML=rows.slice(0,3).map(podiumCard).join("");elements.wall.innerHTML=rows.slice(3).map(nameTile).join("");
  if(rows.length<4)elements.wall.hidden=true;
  populateMyRank(board.campId,rows);
}
function nameLengthClass(row){return Array.from(String(row.studentName||"")).length>6?"long-name":"";}
function cardAttrs(row){return `data-student-id="${escapeHtml(row.studentId)}" tabindex="0" role="button" aria-label="查看${escapeHtml(row.studentName)}的积分构成"`;}
function podiumCard(row){return `<article class="rank-card podium-card ${nameLengthClass(row)} level-${row.level}" data-rank="${row.rank}" ${cardAttrs(row)}><span class="medal medal-${row.rank}" aria-label="第 ${row.rank} 名"><small>TOP</small><strong>${row.rank}</strong></span><div class="avatar avatar-large">${escapeHtml(avatarText(row.studentName))}</div><div class="student-name">${escapeHtml(row.studentName)}</div><div class="score-line"><span class="score">${row.totalPoints} 积分</span></div></article>`;}
function nameTile(row){const band=row.rank<=10?"rank-4-10":row.rank<=30?"rank-11-30":"";return `<article class="rank-card name-tile ${band} ${nameLengthClass(row)} level-${row.level}" ${cardAttrs(row)}><span class="rank-badge">${row.rank}</span><div class="avatar">${escapeHtml(avatarText(row.studentName))}</div><div class="student-copy"><div class="student-name">${escapeHtml(row.studentName)}</div><div class="score-line"><span class="meta">${row.totalPoints} 积分</span></div></div></article>`;}
function showStatus(message){elements.status.hidden=false;elements.status.textContent=message;elements.podium.hidden=true;elements.wall.hidden=true;}
function setScope(scope,reload=true){state.scope=scope;document.querySelectorAll("[data-scope]").forEach(button=>button.classList.toggle("active",button.dataset.scope===scope));elements.classField.hidden=scope==="camp";if(reload)loadBoard();}

function scoreDetails(row){const trend=trendView(row.rankChange,row.previousRank);return `<h3>${escapeHtml(row.studentName)} · ${row.totalPoints} 积分</h3><dl><div><dt>完课</dt><dd>${row.completionPoints}</dd></div><div><dt>课上作业</dt><dd>${row.inclassPoints}</dd></div><div><dt>课后作业</dt><dd>${row.homeworkPoints}</dd></div><div><dt>综合正确率</dt><dd>${Number(row.accuracyRate).toFixed(1)}%</dd></div><div><dt>排名趋势</dt><dd>${escapeHtml(trend.title)}</dd></div></dl>`;}
function positionPopover(card){const rect=card.getBoundingClientRect(),box=elements.popover.getBoundingClientRect(),margin=10;let top=rect.top-box.height-margin;if(top<margin)top=Math.min(innerHeight-box.height-margin,rect.bottom+margin);let left=rect.left+(rect.width-box.width)/2;left=Math.max(margin,Math.min(innerWidth-box.width-margin,left));elements.popover.style.transform=`translate(${Math.round(left)}px,${Math.round(top)}px)`;}
function showPopover(card,pinned=false){const row=state.rows.find(item=>String(item.studentId)===card.dataset.studentId);if(!row)return;state.pinnedId=pinned?card.dataset.studentId:null;elements.popover.innerHTML=scoreDetails(row);elements.popover.hidden=false;requestAnimationFrame(()=>positionPopover(card));}
function hidePopover(force=false){if(state.pinnedId&&!force)return;state.pinnedId=null;elements.popover.hidden=true;}
function cardFrom(event){return event.target.closest?.(".rank-card");}
document.querySelector("main").addEventListener("pointerover",event=>{const card=cardFrom(event);if(card&&event.pointerType==="mouse"&&!card.contains(event.relatedTarget))showPopover(card);});
document.querySelector("main").addEventListener("pointerout",event=>{const card=cardFrom(event);if(card&&event.pointerType==="mouse"&&!card.contains(event.relatedTarget))hidePopover();});
document.querySelector("main").addEventListener("focusin",event=>{const card=cardFrom(event);if(card)showPopover(card);});
document.querySelector("main").addEventListener("focusout",event=>{const card=cardFrom(event);if(card&&!card.contains(event.relatedTarget))hidePopover();});
document.querySelector("main").addEventListener("click",event=>{const card=cardFrom(event);if(!card)return;if(state.pinnedId===card.dataset.studentId)hidePopover(true);else showPopover(card,true);});
document.querySelector("main").addEventListener("keydown",event=>{const card=cardFrom(event);if(!card)return;if(event.key==="Enter"||event.key===" "){event.preventDefault();card.click();}if(event.key==="Escape")hidePopover(true);});
document.addEventListener("click",event=>{if(!cardFrom(event)&&!elements.popover.contains(event.target))hidePopover(true);});
addEventListener("resize",()=>{if(!elements.popover.hidden){const id=state.pinnedId;if(!id){hidePopover(true);return;}const card=document.querySelector(`.rank-card[data-student-id="${CSS.escape(id)}"]`);if(card)positionPopover(card);else hidePopover(true);}});

function storageKey(campId){return`codedog-ranking-student:${campId}`;}
function populateMyRank(campId,rows){
  const stored=localStorage.getItem(storageKey(campId))||"",preferred=state.myStudentId||stored;
  elements.mySelect.innerHTML=`<option value="">选择姓名</option>${rows.map(row=>option(row.studentId,`${row.studentName} · 第 ${row.rank} 名`)).join("")}`;
  state.myStudentId=rows.some(row=>String(row.studentId)===String(preferred))?String(preferred):"";
  elements.mySelect.value=state.myStudentId;elements.myBar.hidden=false;renderMyRank();
}
function renderMyRank(){
  const index=state.rows.findIndex(row=>String(row.studentId)===String(state.myStudentId));
  if(index<0){elements.myContent.hidden=true;return;}
  const row=state.rows[index],gap=pointsToPass(state.rows,index),message=index===0?"当前已是第 1 名":`距离超越上一名还差 ${gap} 分`;
  elements.myContent.innerHTML=`<div class="avatar">${escapeHtml(avatarText(row.studentName))}</div><strong>第 ${row.rank} 名</strong><span class="my-name">${escapeHtml(row.studentName)}</span><span class="my-points">${row.totalPoints} 积分</span><span class="my-motivation">${message}</span>`;
  elements.myContent.hidden=false;
}

elements.camp.addEventListener("change",()=>{populateClasses();loadBoard();});elements.class.addEventListener("change",loadBoard);
elements.mySelect.addEventListener("change",()=>{state.myStudentId=elements.mySelect.value;if(state.myStudentId)localStorage.setItem(storageKey(elements.camp.value),state.myStudentId);else localStorage.removeItem(storageKey(elements.camp.value));updateQuery();renderMyRank();});
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
