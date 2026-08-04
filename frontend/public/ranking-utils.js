export function avatarText(name){
  const chars=Array.from(String(name||"").trim());
  return chars.slice(-2).join("")||"?";
}

export function trendView(rankChange,previousRank){
  if(previousRank==null)return{className:"trend-same",label:"-",title:"暂无历史排名"};
  if(rankChange>0)return{className:"trend-up",label:`↑ ${rankChange}`,title:`上升 ${rankChange} 名`};
  if(rankChange<0)return{className:"trend-down",label:`↓ ${Math.abs(rankChange)}`,title:`下降 ${Math.abs(rankChange)} 名`};
  return{className:"trend-same",label:"-",title:"排名持平"};
}

export function pointsToPass(rows,index){
  if(index<=0)return 0;
  const currentPoints=Number(rows[index].totalPoints);
  for(let previous=index-1;previous>=0;previous--){
    const previousPoints=Number(rows[previous].totalPoints);
    if(previousPoints>currentPoints)return previousPoints-currentPoints+1;
  }
  return 0;
}
