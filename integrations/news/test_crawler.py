import os, time, threading, unittest
from types import SimpleNamespace
from unittest.mock import patch
os.environ.setdefault('NEWS_WEBHOOK_SECRET','test-only-webhook-secret-at-least-32')
import crawler

class CollectorTest(unittest.TestCase):
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
