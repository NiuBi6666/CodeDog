
export function relativeTime(value, now=Date.now()) {
  const date=new Date(value),delta=now-date.getTime();
  if(!Number.isFinite(delta))return '时间待更新';
  if(delta>=0&&delta<60000)return '刚刚';
  if(delta>=60000&&delta<3600000)return Math.floor(delta/60000)+'分钟前';
  if(delta>=3600000&&delta<86400000)return Math.floor(delta/3600000)+'小时前';
  return (date.getFullYear()===new Date(now).getFullYear()?'':date.getFullYear()+'年')+(date.getMonth()+1)+'月'+date.getDate()+'日';
}
const stop=new Set('a an the in on at to for of and or as with by from into over after before ahead reportedly report reports launches launch hopes hope new releases release unveils unveil artificial intelligence model models company companies'.split(' '));
const aliases={strikes:'strike',struck:'strike',attacks:'strike',attack:'strike',iranian:'iran',hikes:'hike',rates:'rate',rejects:'reject'};
function tokens(title){
  return new Set((title||'').toLowerCase().replace(/u\.s\./g,'us').replace(/[’']s\b/g,'').match(/[a-z0-9]+/g)?.filter(w=>!stop.has(w)).map(w=>aliases[w]||w)||[]);
}
export function sameStory(a,b){
  if(a.source_name&&a.source_name===b.source_name)return false;
  const delta=Math.abs(new Date(a.published_at)-new Date(b.published_at));
  if(!Number.isFinite(delta)||delta>24*3600000)return false;
  const x=tokens(a.title_en),y=tokens(b.title_en);
  if(!x.size||!y.size)return false;
  const digits=set=>[...set].filter(w=>/\d/.test(w)).sort().join(',');
  if(digits(x)!==digits(y))return false;
  if(['not','no','denies','denied'].some(w=>x.has(w)!==y.has(w)))return false;
  if(['qeshm','kharg'].some(w=>x.has(w))&&['qeshm','kharg'].some(w=>y.has(w))&&x.has('qeshm')!==y.has('qeshm'))return false;
  const shared=[...x].filter(w=>y.has(w)).length;
  const containment=shared/Math.min(x.size,y.size),jaccard=shared/(x.size+y.size-shared);
  return (shared>=4&&containment>=.75&&jaccard>=.3)||(shared>=3&&jaccard>=.85);
}
export function groupStories(articles){
  const groups=[];
  for(const article of articles){
    const match=groups.slice(-3).find(g=>sameStory(g,article));
    if(match)match.folded.push(article);
    else groups.push({...article,folded:[]});
  }
  return groups;
}
