
import { describe,it,expect } from 'vitest';
import { newsImageUrl } from './news-images.js';
describe('same origin image paths',()=>{
 it('encodes signed URLs without modifying signatures',()=>{
 const u='https://i.guim.co.uk/a.jpg?width=640&s=ab%2Bc&crop=1,2';
 expect(newsImageUrl(u)).toBe('/news/media/preview?url='+encodeURIComponent(u));
 expect(newsImageUrl(u,true)).toBe('/news/media/original?url='+encodeURIComponent(u));
 });
 it('keeps CNBC on its fixed upstream path',()=>{
 expect(newsImageUrl('https://image.cnbcfm.com/api/v1/image/a.jpg?v=12&w=1920')).toBe('/news/media/cnbc/api/v1/image/a.jpg?v=12&w=1920');
 });
 it('rejects unsafe source schemes',()=>{expect(newsImageUrl('http://localhost/a')).toBe('');expect(newsImageUrl('data:image/svg+xml,a')).toBe('');});
});
