import os, time, threading, unittest
from types import SimpleNamespace
from unittest.mock import patch
os.environ.setdefault('NEWS_WEBHOOK_SECRET','test-only-webhook-secret-at-least-32')
import crawler

class CollectorTest(unittest.TestCase):
 def setUp(self):
  self.reader=patch.object(crawler,'jina_article',side_effect=ValueError('offline test'));self.reader.start();self.addCleanup(self.reader.stop)
  self.mode=patch.object(crawler,'FAST',False);self.mode.start();self.addCleanup(self.mode.stop)
  self.deadline=patch.object(crawler,'DEADLINE',float('inf'));self.deadline.start();self.addCleanup(self.deadline.stop)

 def test_chinese_content_uses_characters_not_english_word_count(self):
  self.assertTrue(crawler.enough_content('这是中文新闻正文。'*30,'zh'))
  self.assertFalse(crawler.enough_content('短新闻','zh'))
  self.assertFalse(crawler.enough_content('An English fragment.','en'))
  source={'id':7,'rss_url':'https://example.com/feed','lang':'zh'}
  text='这是保留原文的完整中文新闻段落。'*30
  with patch.object(crawler,'download',return_value=(b'html','https://example.com/a')),patch.object(crawler.trafilatura,'bare_extraction',return_value={'text':text,'title':'中文原文标题'}):
   article=crawler.extract_article({'link':'https://example.com/a','title':'标题'},source)
  self.assertEqual(article['paragraphs_en'],[text])
  self.assertEqual(article['title_en'],'标题')

 def test_explicit_rss_full_content_can_replace_unreachable_page_but_summary_cannot(self):
  text='An original report with facts and complete paragraphs. '*25
  entry={'link':'https://example.com/article','title':'RSS original title','content':[{'value':'<p>'+text+'</p><script>bad()</script>'}]}
  with patch.object(crawler,'download',side_effect=ConnectionError()),patch.object(crawler,'FAST',False):
   result=crawler.extract_article(entry,self.source())
   self.assertIsNotNone(result)
   self.assertNotIn('bad()',result['paragraphs_en'][0])
   self.assertIsNone(crawler.extract_article({'link':entry['link'],'summary':text,'title':'Summary only'},self.source()))


 def test_36kr_official_full_description_preserves_paragraphs_without_fetching_blocked_page(self):
  source={'id':28,'lang':'zh','rss_url':'https://www.36kr.com/feed'}
  text='这是来自官方订阅源的完整新闻报道，保留原始事实与段落。'*20
  entry={'link':'https://36kr.com/p/123?f=rss','title':'新闻标题','summary':'<p>'+text+'</p><p>第二段正文。</p><script>bad()</script>'}
  with patch.object(crawler,'download') as fetch:
   result=crawler.extract_article(entry,source)
   self.assertEqual(result['paragraphs_en'],[text,'第二段正文。'])
   fetch.assert_not_called()
  self.assertIsNone(crawler.rss_fulltext(entry,{'rss_url':'https://example.com/feed'}))
  entry['link']='https://36kr.com.example.com/p/123'
  self.assertIsNone(crawler.rss_fulltext(entry,source))

 def test_corrupted_36kr_feed_recovers_from_official_page_and_preserves_original_url(self):
  source={'id':28,'lang':'zh','rss_url':'https://www.36kr.com/feed'}
  clean='对比当地车企薪资被认为竞争力不足。'*30
  broken=clean.replace('当地','当\ufffd\ufffd\ufffd',1)
  entry={'link':'https://36kr.com/p/123?f=rss','title':'新闻标题','summary':'<p>'+broken+'</p>'}
  self.assertIsNone(crawler.rss_fulltext(entry,source))
  with patch.object(crawler,'download',return_value=(b'<html></html>','https://www.36kr.com/p/123?f=rss')) as fetch,patch.object(crawler.trafilatura,'bare_extraction',return_value={'text':clean}):
   result=crawler.extract_article(entry,source)
   fetch.assert_called_once_with('https://www.36kr.com/p/123?f=rss')
   self.assertEqual(result['origin_url'],entry['link'])
   self.assertEqual(result['paragraphs_en'],[clean])
  with patch.object(crawler,'download',side_effect=ConnectionError()),patch.object(crawler,'FAST',False):
   self.assertIsNone(crawler.extract_article(entry,source))
  with patch.object(crawler,'download',return_value=(b'<html></html>','https://www.36kr.com/p/123')),patch.object(crawler.trafilatura,'bare_extraction',return_value={'text':broken}):
   self.assertIsNone(crawler.extract_article(entry,source))

 def test_footer_cleanup_is_conservative_and_rechecks_minimum_body_length(self):
  body=('The investigation took place on November 4 and the report preserves its chronology. '*18).strip()
  dates=['- 2026年11月04日 11:06:43','- 2026年11月03日 17:12:22']
  for heading in ['国际新闻精选：','经济新闻精选：','财经新闻精选：','社会新闻精选：','文化新闻精选：','港澳台新闻精选：']:
   with self.subTest(heading=heading):self.assertEqual(crawler.clean_paragraphs([body,heading,*dates]),[body])
  self.assertEqual(crawler.clean_paragraphs([body,*dates]),[body,*dates])
  self.assertEqual(crawler.clean_paragraphs([body,'Read more:','The minister announced new measures.',*dates]),[body,'Read more:','The minister announced new measures.',*dates])
  with patch.object(crawler,'download',return_value=(b'<p>x</p>','https://example.com/a')),patch.object(crawler.trafilatura,'bare_extraction',return_value={'text':'Short article.\nRelated stories:\n'+'\n'.join(['[More news](https://example.com/a)']*200)}):
   self.assertIsNone(crawler.extract_article({'link':'https://example.com/a'},self.source()))

 def test_known_publisher_recommendations_are_removed_before_extraction(self):
  raw='<html><head><title>Report title</title></head><body><div class="left_zw"><p>Original report with an event date: 2026-11-04 11:06:43.</p></div><div><h4 class="newsRecommendTitle">国际新闻精选：</h4><ul><li>Unrelated story 2026-11-03</li></ul></div><footer>Subscribe</footer></body></html>'
  cleaned=crawler.prepare_article_html(raw.encode(),'https://www.chinanews.com.cn/gj/story.shtml')
  self.assertIn('Original report with an event date',cleaned)
  self.assertIn('Report title',cleaned)
  self.assertNotIn('Unrelated story',cleaned)
  self.assertNotIn('Subscribe',cleaned)
  # The same CSS class on an unrelated publisher must not truncate its article.
  self.assertIn('Unrelated story',crawler.prepare_article_html(raw.encode(),'https://example.com/story'))

 def test_rss_fulltext_and_reader_fallback_get_the_same_footer_cleanup(self):
  body='Original article text with facts and quotations. '*30
  footer='\nRelated stories:\n[First](https://example.com/1)\n[Second](https://example.com/2)'
  with patch.object(crawler,'download',side_effect=ConnectionError()),patch.object(crawler,'FAST',True),patch.object(crawler,'jina_article',return_value={'text':body+footer}):
   result=crawler.extract_article({'link':'https://example.com/a'},self.source())
   self.assertEqual(result['paragraphs_en'],[body.strip()])
  entry={'link':'https://example.com/a','content':[{'value':'<p>'+body+'</p><p>Related stories:</p><p>[First](https://example.com/1)</p><p>[Second](https://example.com/2)</p>'}]}
  with patch.object(crawler,'download',side_effect=ConnectionError()),patch.object(crawler,'FAST',False):
   self.assertEqual(crawler.extract_article(entry,self.source())['paragraphs_en'],[body.strip()])

 def test_gallery_keeps_article_figures_deduplicates_renditions_and_excludes_promos(self):
  raw='''<main><article><figure><img src="https://img.example.com/lead.jpg?w=300" srcset="https://img.example.com/lead.jpg?w=600 600w, https://img.example.com/lead.jpg?w=1800 1800w"></figure><div itemprop="articleBody"><figure><img src="/second.jpg"></figure><figure><img src="https://img.example.com/lead.jpg?w=100"></figure><img width="32" src="/avatar.jpg"><figure><img src="data:image/png,abc"></figure></div><aside><figure><img src="/related.jpg"></figure></aside><div class="onward"><figure><img src="/recommend.jpg"></figure></div></article><figure><img src="/outside.jpg"></figure></main>'''
  self.assertEqual(crawler.article_images(raw,'https://publisher.example/story'),['https://img.example.com/lead.jpg?w=1800','https://publisher.example/second.jpg'])
  self.assertEqual(crawler.merge_images(['https://img.example.com/a.jpg?w=1800'],['https://img.example.com/a.jpg?w=100','https://img.example.com/b.jpg']),['https://img.example.com/a.jpg?w=1800','https://img.example.com/b.jpg'])

 def test_picture_uses_high_resolution_sources_and_skips_video_previews(self):
  raw='<article><figure><picture><source srcset="https://img.example.com/full.jpg 1800w"><img src="https://img.example.com/thumb.jpg"></picture></figure><figure><video></video><img src="/video.jpg"></figure></article>'
  self.assertEqual(crawler.article_images(raw,'https://example.com/news'),['https://img.example.com/full.jpg'])
  raw='<article><figure><img src="https://i.guim.co.uk/img/media/'+'a'*40+'/0_0_2000_2666/master/2000.jpg?width=445&amp;crop=none"></figure></article>'
  value=crawler.article_images(raw,'https://www.theguardian.com/a')[0]
  self.assertIn('width=445',value)
  self.assertIn('crop=none',value)

 def test_guardian_crop_and_social_overlay_are_one_original_asset(self):
  asset='a'*40
  self.assertEqual(crawler.image_identity('https://i.guim.co.uk/img/media/'+asset+'/0_0_100_100/master/100.jpg?w=1200'),crawler.image_identity('https://i.guim.co.uk/img/media/'+asset+'/467_97_1218_975/master/1218.jpg?overlay=test'))

 def test_gallery_survives_feed_and_direct_extraction(self):
  text='Original facts and quotations. '*30
  entry={'link':'https://example.com/a','title':'News','content':[{'value':'<p>'+text+'</p><figure><img src="/first.jpg"></figure><figure><img src="/second.jpg"></figure>'}]}
  result=crawler.extract_article(entry,self.source())
  self.assertEqual(result['images'],['https://example.com/first.jpg','https://example.com/second.jpg'])
  self.assertEqual(result['cover_image'],result['images'][0])
  with patch.object(crawler,'download',return_value=(b'<article><figure><img src="/first.jpg"></figure><figure><img src="/second.jpg"></figure></article>','https://example.com/a')),patch.object(crawler.trafilatura,'bare_extraction',return_value={'text':text,'image':'https://example.com/first.jpg'}):
   result=crawler.extract_article({'link':'https://example.com/a'},self.source())
   self.assertEqual(result['images'],['https://example.com/first.jpg','https://example.com/second.jpg'])

 def test_signed_image_urls_remain_byte_for_byte_intact(self):
  signed='https://i.guim.co.uk/img/media/'+'b'*40+'/0_0_1200_800/master/1200.jpg?width=620&height=400&precrop=40:21,offset-x50,offset-y0&overlay-base64=a%2Fb%3D&s=signature&dpr=2'
  second=signed.replace('width=620','width=1200').replace('s=signature','s=another-signature')
  raw='<article><figure><img src="'+signed.replace('&','&amp;')+'" srcset="'+signed.replace('&','&amp;')+' 620w, '+second.replace('&','&amp;')+' 1200w"></figure></article>'
  self.assertEqual(crawler.article_images(raw,'https://www.theguardian.com/a'),[second])
  self.assertEqual(crawler.merge_images([signed],[second]),[signed])
  other='https://images.example.com/photo.jpg?width=300&Signature=abc%2Fdef&Expires=42'
  self.assertEqual(crawler.merge_images([other]),[other])

 def test_srcset_preserves_query_commas_and_supports_density_descriptors(self):
  self.assertEqual(crawler.srcset_candidates('https://example.com/a?crop=1,2,3 600w, https://example.com/b 1200w'),[(600,'https://example.com/a?crop=1,2,3'),(1200,'https://example.com/b')])
  self.assertEqual(crawler.srcset_candidates('https://example.com/a 1x, https://example.com/b 2x'),[(1,'https://example.com/a'),(2,'https://example.com/b')])

 def test_historical_image_repair_matches_assets_and_retains_unresolved_images(self):
  from repair_images import restore_images
  base='https://i.guim.co.uk/img/media/'
  old=base+'a'*40+'/master.jpg?width=1800&s=broken'
  original=base+'a'*40+'/master.jpg?width=620&s=valid'
  unrelated=base+'b'*40+'/other.jpg?s=valid'
  self.assertEqual(restore_images([old],[unrelated,original],lambda u:u==original),([original],True,0))
  self.assertEqual(restore_images([old],[unrelated],lambda u:False),([old],False,1))
  self.assertEqual(restore_images([original],[old],lambda u:u==original),([original],False,0))

 def source(self,n=1):return {'id':n,'rss_url':'https://example.com/rss'}
 def test_batches_arrive_before_slow_article_and_completion_is_last(self):
  slow=threading.Event();first_push=threading.Event();sent=[]
  def article(entry,source):
   if entry==5:
    self.assertTrue(first_push.wait(1))
    slow.set()
   return {'id':entry}
  def send(rows,results):
   sent.append((list(rows),results))
   if len(rows)==3 and not first_push.is_set():
    self.assertFalse(slow.is_set());first_push.set()
  with patch.object(crawler,'download',return_value=(b'feed','url')),patch.object(crawler.feedparser,'parse',return_value=SimpleNamespace(entries=list(range(6)))),patch.object(crawler,'unseen',side_effect=lambda e:e),patch.object(crawler,'article',side_effect=article),patch.object(crawler,'send',side_effect=send):
   crawler.collect_source(self.source())
  self.assertEqual(sorted(a['id'] for rows,_ in sent for a in rows),list(range(6)))
  self.assertEqual(sent[-1][0],[])
  self.assertTrue(sent[-1][1][0]['success'])
  self.assertEqual(sent[-1][1][0]['stats']['accepted_articles'],6)
  self.assertTrue(all(len(rows)<=3 for rows,_ in sent))

 def test_preflight_skips_existing_urls_and_duplicate_entries(self):
  entries=[{'link':'https://example.com/old'},{'link':'https://example.com/new'},{'link':'https://example.com/new'}]
  response=SimpleNamespace(raise_for_status=lambda:None,json=lambda:{'urls':['https://example.com/new']})
  with patch.object(crawler,'session',return_value=SimpleNamespace(post=lambda *a,**k:response)):
   self.assertEqual(crawler.unseen(entries),[entries[1]])

 def test_preflight_failure_does_not_download_articles(self):
  with patch.object(crawler,'download',return_value=(b'feed','url')),patch.object(crawler.feedparser,'parse',return_value=SimpleNamespace(entries=[{'link':'https://example.com/a'}])),patch.object(crawler,'unseen',side_effect=RuntimeError()),patch.object(crawler,'article') as article,patch.object(crawler,'send'):
   self.assertFalse(crawler.collect_source(self.source()))
   article.assert_not_called()

 def test_fast_path_limits_to_latest_three(self):
  entries=[{'link':'https://example.com/'+str(i)} for i in range(10)]
  with patch.object(crawler,'FAST',True),patch.object(crawler,'download',return_value=(b'feed','url')),patch.object(crawler.feedparser,'parse',return_value=SimpleNamespace(entries=entries)),patch.object(crawler,'unseen',side_effect=lambda e:e) as preflight,patch.object(crawler,'article',return_value={'id':1}) as article,patch.object(crawler,'send'):
   self.assertTrue(crawler.collect_source(self.source()))
   self.assertEqual(len(preflight.call_args.args[0]),3)
   self.assertEqual(article.call_count,3)

 def test_gateway_hands_off_before_slow_sources_finish(self):
  import gateway,json,requests
  from http.server import ThreadingHTTPServer
  release=threading.Event();finished=threading.Event()
  def run(internal=False):
   crawler.ON_FAILURE()
   release.wait(2);finished.set()
   return True
  server=ThreadingHTTPServer(('127.0.0.1',0),gateway.Handler)
  thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
  try:
   with patch.dict(os.environ),patch.object(crawler,'run',side_effect=run),patch.object(crawler,'FAST',False),patch.object(crawler,'SECRET',crawler.SECRET),patch.object(crawler,'DEADLINE',float('inf')),patch.object(crawler,'ON_FAILURE',None):
    try:
     start=time.monotonic()
     r=requests.post('http://127.0.0.1:%s/collect'%server.server_port,json={'run_id':77,'secret':'test-secret-at-least-32-characters'},timeout=1)
     self.assertLess(time.monotonic()-start,1)
     self.assertEqual(r.json(),{'acknowledged':True,'fallback':True})
     self.assertFalse(finished.is_set())
    finally:release.set();finished.wait(2)
  finally:server.shutdown();server.server_close()

 def test_jina_rejects_error_json_and_private_targets(self):
  with patch.object(crawler,'public_url'),patch.object(crawler,'download',return_value=(b'{"code":429,"data":{"content":"bad"}}','url')):
   with self.assertRaises(ValueError):crawler.jina_article('https://example.com/a')
  with self.assertRaises(ValueError):crawler.public_url('https://127.0.0.1/a')

 def test_feed_failure_and_webhook_failure_are_distinct(self):
  with patch.object(crawler,'download',side_effect=ValueError()),patch.object(crawler,'send') as send:
   crawler.collect_source(self.source())
   self.assertFalse(send.call_args.args[1][0]['success'])
   self.assertEqual(send.call_args.args[1][0]['stats']['feed_error'],'feed_or_preflight_failed')
  with patch.object(crawler,'download',return_value=(b'feed','url')),patch.object(crawler.feedparser,'parse',return_value=SimpleNamespace(entries=[1])),patch.object(crawler,'unseen',side_effect=lambda e:e),patch.object(crawler,'article',return_value={'id':1}),patch.object(crawler,'send',side_effect=crawler.WebhookError()):
   with self.assertRaises(crawler.WebhookError):crawler.collect_source(self.source())

 def test_two_sources_run_together_and_sessions_are_isolated(self):
  barrier=threading.Barrier(2); sessions=[]
  def collect(source):
   sessions.append(crawler.session())
   barrier.wait(timeout=1)
  response=SimpleNamespace(raise_for_status=lambda:None,json=lambda:{'sources':[self.source(1),self.source(2)]})
  session=SimpleNamespace(get=lambda *a,**k:response)
  with patch.object(crawler,'public_url'),patch.object(crawler,'session',side_effect=lambda: session if threading.current_thread()==threading.main_thread() else crawler.requests.Session()),patch.object(crawler,'collect_source',side_effect=collect),patch.object(crawler,'send') as send:
   crawler.run()
  send.assert_called_once_with([],[],completed=True)
  self.assertEqual(len(sessions),2);self.assertIsNot(sessions[0],sessions[1])

 def test_retries_preserve_batch_identity_and_completion_marker(self):
  import json
  bodies=[]
  def post(url,**kwargs):
   bodies.append(json.loads(kwargs['data']))
   if len(bodies)==1:raise crawler.requests.ConnectionError()
   return SimpleNamespace(raise_for_status=lambda:None)
  with patch.dict(os.environ,{'NEWS_RUN_ID':'123'}),patch.object(crawler,'session',return_value=SimpleNamespace(post=post)),patch.object(crawler.time,'sleep'):
   crawler.send([],[],completed=True)
  self.assertEqual(len(bodies),2)
  self.assertEqual(bodies[0],bodies[1])
  self.assertEqual(bodies[0]['run_id'],123)
  self.assertTrue(bodies[0]['collection_finished'])

 def test_entire_feed_is_checked_and_preflight_is_chunked(self):
  entries=[{'link':'https://example.com/'+str(i)} for i in range(237)]
  batches=[]
  def post(url,**kw):
   batches.append(kw['json']['urls']);return SimpleNamespace(raise_for_status=lambda:None,json=lambda:{'urls':[u for u in kw['json']['urls'] if u.endswith('/236')]})
  with patch.object(crawler,'session',return_value=SimpleNamespace(post=post)):
   self.assertEqual(crawler.unseen(entries+[entries[-1]]),[entries[-1]])
  self.assertEqual([len(b) for b in batches],[100,100,37])
  with patch.object(crawler,'download',return_value=(b'feed','url')),patch.object(crawler.feedparser,'parse',return_value=SimpleNamespace(entries=entries)),patch.object(crawler,'unseen',return_value=[]) as check,patch.object(crawler,'send') as send:
   crawler.collect_source(self.source());self.assertEqual(len(check.call_args.args[0]),237)
   self.assertEqual(send.call_args.args[1][0]['stats']['rss_entries'],237)

 def test_verified_short_brief_and_full_feed_metadata(self):
  entry={'link':'https://36kr.com/newsflashes/123','title':'官方快讯','summary':'<p>'+'最新官方消息已经确认发布，保留完整原文。'*2+'</p>','published':'Fri, 06 Nov 2026 18:00:00 +0800','media_thumbnail':[{'url':'https://example.com/image.jpg'}]}
  with patch.object(crawler,'download') as download:
   result=crawler.extract_article(entry,{'id':28,'rss_url':'https://36kr.com/feed','lang':'zh'})
   self.assertEqual(result['content_kind'],'brief');self.assertEqual(result['published_at'],'2026-11-06T10:00:00Z')
   self.assertEqual(result['cover_image'],'https://example.com/image.jpg');download.assert_not_called()
  with patch.object(crawler,'download',side_effect=ConnectionError()):
   self.assertIsNone(crawler.extract_article(entry,self.source()))
  text=' '.join(['facts']*40)
  self.assertTrue(crawler.enough_content(text,'en'))
  self.assertTrue(crawler.enough_content('新闻事实'*16,'zh'))

 def test_partial_extraction_reports_counts_and_reasons(self):
  entries=[{'link':'https://example.com/'+str(i),'title':'Report'} for i in range(3)]
  def inspect(e,s):return ({'id':1},None) if e==entries[0] else (None,'blocked')
  with patch.object(crawler,'download',return_value=(b'feed','url')),patch.object(crawler.feedparser,'parse',return_value=SimpleNamespace(entries=entries)),patch.object(crawler,'unseen',side_effect=lambda e:e),patch.object(crawler,'inspect_article',side_effect=inspect),patch.object(crawler,'send') as send:
   self.assertFalse(crawler.collect_source(self.source()))
   report=send.call_args.args[1][0];self.assertFalse(report['success'])
   self.assertEqual(report['stats']['accepted_articles'],1);self.assertEqual(report['stats']['rejected_articles'],2)
   self.assertEqual(report['stats']['reasons'],{'blocked':2});self.assertEqual(len(report['stats']['rejected']),2)

if __name__=='__main__':unittest.main()
