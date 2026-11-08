import {describe,it,expect,vi,afterEach} from 'vitest';
import {clipExcerpt,loadStoryExcerpt} from './newspaper-excerpts.js';
afterEach(()=>vi.unstubAllGlobals());
describe('newspaper card excerpts',()=>{
 it('limits Chinese to 100 Unicode characters without breaking characters',()=>{const s='正文😀'.repeat(50);expect(Array.from(clipExcerpt(s)).length).toBe(101);expect(clipExcerpt(s)).not.toContain('\uFFFD');});
 it('keeps short paragraphs unchanged and avoids incomplete English words',()=>{expect(clipExcerpt('short paragraph')).toBe('short paragraph');expect(clipExcerpt('A complete word follows',13)).toBe('A complete…');});
 it('deduplicates concurrent reads, caches extracted text and only uses GET',async()=>{
  const fetch=vi.fn(async()=>({ok:true,json:async()=>({title_en:'Title',content_en:'Title\n\n- Published\n\nOriginal paragraph.',content_zh:'标题\n\n已发布\n\n真实导语。'})}));
  vi.stubGlobal('fetch',fetch);
  const [a,b]=await Promise.all([loadStoryExcerpt({id:90001}),loadStoryExcerpt({id:90001})]);
  expect(a).toEqual([{original:'Original paragraph.',translation:'真实导语。'}]);expect(b).toEqual(a);
  await loadStoryExcerpt({id:90001});expect(fetch).toHaveBeenCalledTimes(1);expect(fetch.mock.calls[0][1].method).toBeUndefined();
 });
 it('limits concurrent requests to two and releases slots after failures',async()=>{
  let active=0,max=0;const gates=[];
  vi.stubGlobal('fetch',vi.fn(()=>new Promise(resolve=>{active++;max=Math.max(max,active);gates.push(()=>{active--;resolve({ok:true,json:async()=>({content_en:'Body.'})});});})));
  const work=[1,2,3].map(i=>loadStoryExcerpt({id:91000+i}));
  expect(gates).toHaveLength(2);gates.shift()();await vi.waitFor(()=>expect(gates).toHaveLength(2));gates.splice(0).forEach(release=>release());await Promise.all(work);expect(max).toBe(2);
  const fetch=vi.fn().mockResolvedValueOnce({ok:false}).mockResolvedValueOnce({ok:true,json:async()=>({content_en:'Recovered body.'})});vi.stubGlobal('fetch',fetch);
  await expect(loadStoryExcerpt({id:92001})).rejects.toThrow();expect(await loadStoryExcerpt({id:92001})).toEqual([{original:'Recovered body.',translation:''}]);
 });
});
