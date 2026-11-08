import {describe,it,expect} from 'vitest';
import {leadExcerpt} from './newspaper-layout.js';
describe('newspaper lead excerpt',()=>{
 it('skips repeated titles and metadata while retaining paragraph pairing',()=>{
  const article={title_en:"'Cancer treatment plummeted me into menopause'",title_zh:'癌症治疗让我陷入更年期。',content_en:"'Cancer treatment plummeted me into menopause'\n\n- Published\n\nWhen Jo was diagnosed, she knew treatment would be difficult.\n\nBefore her diagnosis she had been taking HRT.\n\nPart of her treatment included surgery.\n\nA fourth paragraph.",content_zh:'癌症治疗让我陷入更年期。\n\n- 已发布\n\n确诊时她知道治疗很艰难。\n\n确诊前她正在接受治疗。\n\n治疗的一部分包括手术。\n\n第四段。'};
  expect(leadExcerpt(article)).toEqual([{original:'When Jo was diagnosed, she knew treatment would be difficult.',translation:'确诊时她知道治疗很艰难。'},{original:'Before her diagnosis she had been taking HRT.',translation:'确诊前她正在接受治疗。'},{original:'Part of her treatment included surgery.',translation:'治疗的一部分包括手术。'}]);
 });
 it('does not discard legitimate sentences that start with Published',()=>expect(leadExcerpt({content_en:'Published research changed the treatment.'})[0].original).toBe('Published research changed the treatment.'));
 it('supports native Chinese and partial translations without shifting indices',()=>expect(leadExcerpt({title_zh:'标题',content_original:['标题','正文第一段','正文第二段'],paragraphs_zh:['标题','正文第一段']})).toEqual([{original:'正文第一段',translation:'正文第一段'},{original:'正文第二段',translation:''}]));
 it('handles missing content',()=>expect(leadExcerpt(null)).toEqual([]));
});
