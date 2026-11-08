const lines=value=>Array.isArray(value)?value:(value||'').split(/\n\s*\n/).filter(Boolean);
const key=value=>(value||'').normalize('NFKC').toLowerCase().replace(/[^\p{L}\p{N}]/gu,'');
const metadata=/^(?:[-•]\s*)?(?:published|updated|last updated|已发布|发布时间|更新时间)(?:\s*[:：]?\s*\d.*)?$/i;
export function leadExcerpt(article){
 if(!article)return [];
 const original=lines(article.content_original||article.content_en);
 const translated=lines(article.paragraphs_zh||article.content_zh);
 const titles=new Set([article.title_original,article.title_en,article.title_zh].filter(Boolean).map(key));
 return original.map((text,i)=>({original:text.trim(),translation:(translated[i]||'').trim()}))
  .filter(p=>p.original&&!titles.has(key(p.original))&&!metadata.test(p.original))
  .slice(0,3);
}
