export function syncMessage(s, added=0) {
 const prefix=added>0?'已新增 '+added+' 篇可读报道；':'';
 const pending=Number(s.pending_articles||0), processing=Number(s.processing_articles||0);
 if(s.phase==='failed')return s.message||'采集调度失败，请稍后重试';
 if(s.phase==='uncertain')return s.message||'调度结果待确认，后台不会自动重复触发';
 if(s.phase==='queued')return prefix+'任务已提交，等待调度';
 if(s.phase==='dispatching')return prefix+'正在启动快速抓取，必要时转至海外节点';
 if(s.phase==='crawling')return prefix+'正在抓取最新报道';
 if(s.phase==='ingesting')return prefix+'已收到采集数据，'+s.queued_batches+' 个批次等待入库；标题待翻译 '+pending+' 篇';
 if(s.phase==='translating')return prefix+'正在翻译标题 '+processing+' 篇，另有 '+pending+' 篇排队；打开报道后翻译全文';
 return prefix+(added?'同步完成':'本轮已处理，暂未发现新的可读报道');
}

export function syncExpired(started, now=Date.now()){return now-started>=600000;}
