
import {describe,it,expect} from 'vitest';
import {relativeTime,sameStory,groupStories} from './reading-utils.js';
const now=Date.parse('2026-11-03T08:00:00Z');
const article=(id,title,time='2026-11-03T04:00:00Z')=>({id,title_en:title,published_at:time});
describe('reading timestamps',()=>{
 it('shows relative minutes and hours, dates for older stories and handles invalid timestamps',()=>{
 expect(relativeTime(now-12000,now)).toBe('刚刚');
 expect(relativeTime(now-12*60000,now)).toBe('12分钟前');
 expect(relativeTime(now-2*3600000,now)).toBe('2小时前');
 expect(relativeTime('2026-10-14T00:00:00Z',now)).toBe('10月14日');
 expect(relativeTime('invalid',now)).toBe('时间待更新');
 });
});
describe('conservative nearby headline grouping',()=>{
 const a=article(1,"U.S. reportedly strikes Iran's Qeshm Island as Vance hopes for negotiated settlement with Tehran");
 const b=article(3,'US launches strikes on Iranian island ahead of election');
 it('groups a nearby cross-source event and preserves both articles',()=>{
 const result=groupStories([a,article(2,'Australia hikes rates to fight inflation'),b]);
 expect(result).toHaveLength(2);expect(result[0].folded.map(a=>a.id)).toEqual([3]);
 expect(a).not.toHaveProperty('folded');
 });
 it('does not group unrelated or stale stories',()=>{
 expect(sameStory(a,article(4,'Iran and US resume nuclear negotiations'))).toBe(false);
 expect(sameStory(a,{...b,published_at:'2026-10-30T04:00:00Z'})).toBe(false);
 expect(sameStory({...a,published_at:'invalid'},b)).toBe(false);
 expect(sameStory(article(1,''),article(2,''))).toBe(false);
 });
 it('preserves different model numbers and negated headlines',()=>{
 expect(sameStory(article(1,'OpenAI releases GPT 5 model for developers'),article(2,'OpenAI releases GPT 6 model for developers'))).toBe(false);
 expect(sameStory(article(1,'Court approves California disputed vote counting'),article(2,'Court denies California disputed vote counting'))).toBe(false);
 expect(sameStory(article(1,'Google releases new artificial intelligence model'),article(2,'Amazon releases new artificial intelligence model'))).toBe(false);
 expect(sameStory({...a,source_name:'BBC'},{...b,source_name:'BBC'})).toBe(false);
 expect(sameStory(a,article(9,'US strikes Iranian Kharg island ahead of election'))).toBe(false);
 });
 it('only groups nearby articles and handles identical short titles',()=>{
 expect(sameStory(article(1,'Australia hikes rates'),article(2,'Australia hikes rates'))).toBe(true);
 expect(groupStories([a,...[4,5,6].map(id=>article(id,'Unrelated story number '+id)),b])).toHaveLength(5);
 });
});
