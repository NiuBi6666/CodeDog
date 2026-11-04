import os, time, threading, unittest
from types import SimpleNamespace
from unittest.mock import patch
os.environ.setdefault('NEWS_WEBHOOK_SECRET','test-only-webhook-secret-at-least-32')
import crawler

class CollectorTest(unittest.TestCase):
 def test_chinese_content_uses_characters_not_english_word_count(self):
  self.assertTrue(crawler.enough_content('这是中文新闻正文。'*30,'zh'))
  self.assertFalse(crawler.enough_content('短新闻','zh'))
  self.assertFalse(crawler.enough_content('An English fragment.','en'))
  source={'id':7,'rss_url':'https://example.com/feed','lang':'zh'}
  text='这是保留原文的完整中文新闻段落。'*30
  with patch.object(crawler,'download',return_value=(b'html','https://example.com/a')),patch.object(crawler.trafilatura,'bare_extraction',return_value={'text':text,'title':'中文原文标题'}):
   article=crawler.extract_article({'link':'https://example.com/a','title':'标题'},source)
  self.assertEqual(article['paragraphs_en'],[text])
  self.assertEqual(article['title_en'],'中文原文标题')

 def test_explicit_rss_full_content_can_replace_unreachable_page_but_summary_cannot(self):
  text='An original report with facts and complete paragraphs. '*25
  entry={'link':'https://example.com/article','title':'RSS original title','content':[{'value':'<p>'+text+'</p><script>bad()</script>'}]}
  with patch.object(crawler,'download',side_effect=ConnectionError()),patch.object(crawler,'FAST',False):
   result=crawler.extract_article(entry,self.source())
   self.assertIsNotNone(result)
   self.assertNotIn('bad()',result['paragraphs_en'][0])
   self.assertIsNone(crawler.extract_article({'link':entry['link'],'summary':text,'title':'Summary only'},self.source()))

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
  self.assertEqual(sent[-1],([],[{'source_id':1,'success':True}]))
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
   send.assert_called_once_with([],[{'source_id':1,'success':False}])
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

if __name__=='__main__':unittest.main()
