import{avatarText,pointsToPass,trendView}from"/ranking-utils.js";

history.replaceState(null,"",location.pathname);
const elements={status:document.querySelector("#status"),wall:document.querySelector("#nameWall"),subtitle:document.querySelector("#boardSubtitle"),updated:document.querySelector("#lastUpdated"),popover:document.querySelector("#scorePopover"),mySelect:document.querySelector("#myStudentSelect"),avatar:document.querySelector("#currentAvatar"),points:document.querySelector("#currentPoints"),level:document.querySelector("#currentLevel"),completion:document.querySelector("#completionPoints"),inclass:document.querySelector("#inclassPoints"),homework:document.querySelector("#homeworkPoints"),nextLevelName:document.querySelector("#nextLevelName"),nextLevelPoints:document.querySelector("#nextLevelPoints"),motivation:document.querySelector("#motivationText")};
const state={timer:null,rows:[],myStudentId:localStorage.getItem("codedog-ranking-student:all")||"",pinnedId:null};
const levelMinimums=[0,600,1500,2700,4200,5400];
const levelNames=["石墨","青铜","白银","黄金","蓝宝石","钻石"];
const escapeHtml=value=>String(value??"").replace(/[&<>"']/g,char=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"})[char]);

async function request(path,options={}){const response=await fetch(path,{credentials:"same-origin",...options});const type=response.headers.get("content-type")||"";const data=type.includes("json")?await response.json():null;if(!response.ok)throw new Error(data?.error||"请求失败");return data;}
function option(value,label){return `<option value="${escapeHtml(value)}">${escapeHtml(label)}</option>`;}

async function loadBoard(){
  elements.status.classList.remove("error");showStatus("正在更新排行榜…");
  try{
    const board=await request("/api/public/rankings/all");renderBoard(board);
  }catch(error){elements.status.classList.add("error");showStatus(error.message||"排行榜加载失败");}
}

function renderBoard(board){
  elements.subtitle.textContent=`${board.studentCount||0} 名学员 · 全部课程累计积分`;
  elements.updated.textContent=board.updatedAt?`更新于 ${new Intl.DateTimeFormat("zh-CN",{timeZone:"Asia/Shanghai",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hour12:false}).format(new Date(board.updatedAt))}`:"尚未同步";
  const rows=board.rankings||[];state.rows=rows;if(!rows.length){showStatus("暂无学员积分数据");return;}
  elements.status.hidden=true;elements.wall.hidden=false;elements.wall.innerHTML=rows.map(rankingRow).join("");
  populateStudentPicker(rows);
}
function cardAttrs(row){return `data-student-id="${escapeHtml(row.studentId)}" tabindex="0" role="button" aria-label="查看${escapeHtml(row.studentName)}的积分构成"`;}
function rankingRow(row){const place=row.rank<=3?`place-${row.rank}`:"";return `<article class="rank-card ladder-row ${place} level-${row.level}" ${cardAttrs(row)}><span class="ladder-rank">${row.rank}</span><span class="avatar">${escapeHtml(avatarText(row.studentName))}</span><div class="student-copy"><div class="student-name">${escapeHtml(row.studentName)}</div><span class="level-badge">${escapeHtml(row.levelName)}</span></div><strong class="ladder-points">${row.totalPoints}<small>积分</small></strong></article>`;}
function showStatus(message){elements.status.hidden=false;elements.status.textContent=message;elements.wall.hidden=true;}

function scoreDetails(row){const trend=trendView(row.rankChange,row.previousRank);return `<h3>${escapeHtml(row.studentName)} · ${row.totalPoints} 积分</h3><dl><div><dt>完课</dt><dd>${row.completionPoints}</dd></div><div><dt>课上作业</dt><dd>${row.inclassPoints}</dd></div><div><dt>课后作业</dt><dd>${row.homeworkPoints}</dd></div><div><dt>综合正确率</dt><dd>${Number(row.accuracyRate).toFixed(1)}%</dd></div><div><dt>排名趋势</dt><dd>${escapeHtml(trend.title)}</dd></div></dl>`;}
function positionPopover(anchor){const rect=anchor.getBoundingClientRect(),box=elements.popover.getBoundingClientRect(),margin=10;let top=rect.top-box.height-margin;if(top<margin)top=Math.min(innerHeight-box.height-margin,rect.bottom+margin);let left=rect.left+(rect.width-box.width)/2;left=Math.max(margin,Math.min(innerWidth-box.width-margin,left));elements.popover.style.transform=`translate(${Math.round(left)}px,${Math.round(top)}px)`;}
function showPopover(card,pinned=false){const row=state.rows.find(item=>String(item.studentId)===card.dataset.studentId);if(!row)return;state.pinnedId=pinned?card.dataset.studentId:null;elements.popover.innerHTML=scoreDetails(row);elements.popover.hidden=false;requestAnimationFrame(()=>positionPopover(card));}
function showSelectedDetails(anchor){const row=selectedStudent();if(!row)return;state.pinnedId=row.studentId;elements.popover.innerHTML=scoreDetails(row);elements.popover.hidden=false;requestAnimationFrame(()=>positionPopover(anchor));}
function hidePopover(force=false){if(state.pinnedId&&!force)return;state.pinnedId=null;elements.popover.hidden=true;}
function cardFrom(event){return event.target.closest?.(".rank-card");}
document.querySelector("main").addEventListener("pointerover",event=>{const card=cardFrom(event);if(card&&event.pointerType==="mouse"&&!card.contains(event.relatedTarget))showPopover(card);});
document.querySelector("main").addEventListener("pointerout",event=>{const card=cardFrom(event);if(card&&event.pointerType==="mouse"&&!card.contains(event.relatedTarget))hidePopover();});
document.querySelector("main").addEventListener("focusin",event=>{const card=cardFrom(event);if(card)showPopover(card);});
document.querySelector("main").addEventListener("focusout",event=>{const card=cardFrom(event);if(card&&!card.contains(event.relatedTarget))hidePopover();});
document.querySelector("main").addEventListener("click",event=>{const card=cardFrom(event);if(!card)return;selectStudent(card.dataset.studentId);if(state.pinnedId===card.dataset.studentId)hidePopover(true);else showPopover(card,true);});
document.querySelector("main").addEventListener("keydown",event=>{const card=cardFrom(event);if(!card)return;if(event.key==="Enter"||event.key===" "){event.preventDefault();card.click();}if(event.key==="Escape")hidePopover(true);});
document.addEventListener("click",event=>{if(!cardFrom(event)&&!elements.popover.contains(event.target)&&!event.target.closest?.(".supply-action"))hidePopover(true);});
addEventListener("resize",()=>{if(!elements.popover.hidden){const id=state.pinnedId;if(!id){hidePopover(true);return;}const card=document.querySelector(`.rank-card[data-student-id="${CSS.escape(id)}"]`);if(card)positionPopover(card);else hidePopover(true);}});

function populateStudentPicker(rows){
  const preferred=state.myStudentId;
  elements.mySelect.innerHTML=`<option value="">选择姓名</option>${rows.map(row=>option(row.studentId,`${row.studentName} · 第 ${row.rank} 名`)).join("")}`;
  state.myStudentId=rows.some(row=>String(row.studentId)===String(preferred))?String(preferred):String(rows[0].studentId);
  elements.mySelect.value=state.myStudentId;renderSelectedStudent();
}
function selectedStudent(){return state.rows.find(row=>String(row.studentId)===String(state.myStudentId));}
function selectStudent(studentId){state.myStudentId=String(studentId||"");elements.mySelect.value=state.myStudentId;if(state.myStudentId)localStorage.setItem("codedog-ranking-student:all",state.myStudentId);renderSelectedStudent();}
function renderSelectedStudent(){
  const index=state.rows.findIndex(row=>String(row.studentId)===String(state.myStudentId));
  if(index<0)return;
  const row=state.rows[index],gap=pointsToPass(state.rows,index),nextIndex=Math.min(row.level,levelMinimums.length-1),nextMinimum=levelMinimums[nextIndex],isMax=row.level>=6;
  elements.avatar.textContent=avatarText(row.studentName);elements.points.textContent=`${row.totalPoints} 积分`;elements.level.textContent=`第 ${row.rank} 名 · ${row.levelName}`;
  elements.completion.textContent=`${row.completionPoints} 积分`;elements.inclass.textContent=`${row.inclassPoints} 积分`;elements.homework.textContent=`${row.homeworkPoints} 积分`;
  elements.nextLevelName.textContent=isMax?"最高等级":`下一等级 · ${levelNames[nextIndex]}`;elements.nextLevelPoints.textContent=isMax?"已达钻石":`还差 ${Math.max(0,nextMinimum-row.totalPoints)} 积分`;
  elements.motivation.textContent=row.rank===1?"当前已是全员榜第 1 名，继续保持！":`距离超越上一名还差 ${gap} 分`;
  document.querySelectorAll(".rank-card").forEach(card=>card.classList.toggle("selected",card.dataset.studentId===state.myStudentId));
}

elements.mySelect.addEventListener("change",()=>selectStudent(elements.mySelect.value));
document.querySelectorAll(".supply-action").forEach(button=>button.addEventListener("click",()=>showSelectedDetails(button)));
document.querySelector("#refreshButton").addEventListener("click",loadBoard);
document.querySelector("#fullscreenButton").addEventListener("click",async()=>{if(document.fullscreenElement)await document.exitFullscreen();else await document.documentElement.requestFullscreen();});
document.addEventListener("fullscreenchange",()=>document.body.classList.toggle("is-fullscreen",Boolean(document.fullscreenElement)));

loadBoard();state.timer=setInterval(loadBoard,60_000);
