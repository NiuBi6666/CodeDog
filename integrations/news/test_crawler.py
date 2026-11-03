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
  with patch.object(crawler,'download',return_value=(b'feed','url')),patch.object(crawler.feedparser,'parse',return_value=SimpleNamespace(entries=list(range(6)))),patch.object(crawler,'article',side_effect=article),patch.object(crawler,'send',side_effect=send):
   crawler.collect_source(self.source())
  self.assertEqual(sorted(a['id'] for rows,_ in sent for a in rows),list(range(6)))
  self.assertEqual(sent[-1],([],[{'source_id':1,'success':True}]))
  self.assertTrue(all(len(rows)<=3 for rows,_ in sent))

 def test_feed_failure_and_webhook_failure_are_distinct(self):
  with patch.object(crawler,'download',side_effect=ValueError()),patch.object(crawler,'send') as send:
   crawler.collect_source(self.source())
   send.assert_called_once_with([],[{'source_id':1,'success':False}])
  with patch.object(crawler,'download',return_value=(b'feed','url')),patch.object(crawler.feedparser,'parse',return_value=SimpleNamespace(entries=[1])),patch.object(crawler,'article',return_value={'id':1}),patch.object(crawler,'send',side_effect=crawler.WebhookError()):
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
