import unittest
from service import translate, pieces, RateController
import time, threading

class TranslationTest(unittest.TestCase):
    def test_paragraph_alignment_including_long_input(self):
        source = ['First paragraph.', 'A long sentence. ' * 180, 'Last paragraph.']
        calls = []
        def fake(text, engine):
            calls.append((text, engine))
            return '译文\n一行'
        result = translate({'engine': 'bing', 'paragraphs': source}, fake)
        self.assertEqual(len(result['paragraphs']), 3)
        self.assertTrue(all('\n' not in p for p in result['paragraphs']))
        self.assertCountEqual([t for t, _ in calls], [part for paragraph in source for part in pieces(paragraph)])
        self.assertTrue(all(len(t) <= 1000 for t, _ in calls))
    def test_invalid_input_never_calls_provider(self):
        def forbidden(*args):
            self.fail('provider called for invalid input')
        for body in [{'engine':'unknown','paragraphs':['text']}, {'paragraphs':[]},
                     {'paragraphs':['']}, {'paragraphs':['a'*101001]}, {'paragraphs':[1]}]:
            with self.assertRaises(ValueError):
                translate(body, forbidden)
    def test_empty_result_is_not_published(self):
        result=translate({'paragraphs':['Hello']}, lambda *_: '', RateController((0,0,0)))
        self.assertEqual(result['paragraphs'], [None])

class SealTest(unittest.TestCase):
    def test_github_sealed_box_roundtrip_and_invalid_public_key(self):
        import base64
        from nacl.public import PrivateKey, SealedBox
        from service import seal_secret
        key = PrivateKey.generate()
        plaintext = 'test-only-webhook-value-at-least-32-chars'
        encoded = base64.b64encode(bytes(key.public_key)).decode()
        ciphertext = seal_secret(encoded, plaintext)
        self.assertNotIn(plaintext, ciphertext)
        self.assertEqual(SealedBox(key).decrypt(base64.b64decode(ciphertext)).decode(), plaintext)
        with self.assertRaises(Exception):
            seal_secret('invalid-key', plaintext)

class ParallelTest(unittest.TestCase):
    def test_parallel_preserves_order_and_reduces_mock_latency(self):
        active=0
        maximum=0
        lock=threading.Lock()
        def fake(text, engine):
            nonlocal active, maximum
            with lock:
                active+=1
                maximum=max(maximum, active)
            time.sleep(0.08 if text=='slow' else 0.04)
            with lock:active-=1
            return '译文'+text
        start=time.monotonic()
        result=translate({'paragraphs':['slow','fast','third','fourth']},fake,RateController((0,0,0)))
        elapsed=time.monotonic()-start
        self.assertEqual(result['paragraphs'], ['译文slow','译文fast','译文third','译文fourth'])
        self.assertEqual(maximum,2)
        self.assertLess(elapsed,0.19) # serial mock takes 0.20 seconds
        print('parallel mock seconds=%.3f serial=0.200'%elapsed)

    def test_failure_keeps_successful_neighbour(self):
        def fake(text, engine):
            if text=='bad':raise RuntimeError('429')
            return '成功'
        result=translate({'paragraphs':['good','bad']},fake,RateController((0,0,0)))
        self.assertEqual(result['paragraphs'],['成功',None])

    def test_backoff_and_recovery_after_ten_successes(self):
        c=RateController((5,15,30))
        c.acquire(time.monotonic()+1);c.finish(False,True)
        self.assertEqual(c.status()['parallelism'],1)
        self.assertGreaterEqual(c.status()['cooldown_seconds'],4)
        c.until=0
        c.acquire(time.monotonic()+1);c.finish(False,True)
        self.assertGreaterEqual(c.status()['cooldown_seconds'],14)
        c.until=0
        c.acquire(time.monotonic()+1);c.finish(False,True)
        self.assertGreaterEqual(c.status()['cooldown_seconds'],29)
        c.until=0
        for _ in range(10):
            c.acquire(time.monotonic()+1);c.finish(True)
        self.assertEqual(c.status()['parallelism'],2)

class StreamingTest(unittest.TestCase):
    def test_results_are_emitted_on_completion_not_input_order(self):
        emitted=[]
        def fake(text,engine):
            time.sleep(0.06 if text=='slow' else 0.005)
            return '译文'+text
        result=translate({'paragraphs':['slow','fast']},fake,RateController((0,0,0)),lambda i,text:emitted.append((i,text)))
        self.assertEqual([i for i,_ in emitted],[1,0])
        self.assertEqual(result['paragraphs'],['译文slow','译文fast'])

class SelectionTest(unittest.TestCase):
    def test_selection_has_independent_queue_and_reuses_engine(self):
        from service import _pool
        release=threading.Event()
        occupied=[_pool.submit(release.wait, 2) for _ in range(2)]
        try:
            result=translate({'engine':'bing','paragraphs':['Artificial intelligence']},
                lambda text,engine: '人工智能' if engine=='bing' else None,
                RateController((0,0,0)),selection=True)
            self.assertEqual(result['paragraphs'],['人工智能'])
        finally:
            release.set()
            for future in occupied:future.result()
    def test_selection_validates_length_and_does_not_retry_provider_failure(self):
        count=[]
        def fail(*args):
            count.append(1)
            raise RuntimeError('429')
        result=translate({'paragraphs':['Hello']},fail,RateController((0,0,0)),selection=True)
        self.assertEqual(result['paragraphs'],[None])
        self.assertEqual(len(count),1)
        for paragraphs in [['a'*1001],['one','two']]:
            with self.assertRaises(ValueError):
                translate({'paragraphs':paragraphs},fail,selection=True)

class LocalTranslationTest(unittest.TestCase):
    def setUp(self):
        import service
        service._local_unavailable_until=0

    def test_local_result_and_fallback_only_on_failure(self):
        import service
        from unittest.mock import patch, Mock
        with patch.object(service, 'mtran_piece', return_value='本地译文') as local:
            self.assertEqual(service.translate_piece('Original', 'mtran-bing'),'本地译文')
            local.assert_called_once()
        client=Mock()
        client.translate_text.return_value='备用译文'
        with patch.object(service, 'mtran_piece', side_effect=TimeoutError()), patch.object(service._clients, 'translator', client, create=True):
            self.assertEqual(service.translate_piece('Original', 'mtran-bing'),'备用译文')
            self.assertEqual(service.translate_piece('Next', 'mtran-bing'),'备用译文')
            self.assertEqual(client.translate_text.call_args.kwargs['translator'],'bing')
            self.assertEqual(service.mtran_piece.call_count,1)
            with self.assertRaises(RuntimeError):
                service.translate_piece('Original', 'mtran')

    def test_local_http_contract_and_empty_result_rejected(self):
        import service
        from unittest.mock import patch, MagicMock
        response=MagicMock()
        response.__enter__.return_value=response
        response.read.return_value=__import__('json').dumps({'result':'第一行\n第二行'}).encode()
        with patch('urllib.request.urlopen', return_value=response) as request:
            self.assertEqual(service.mtran_piece('Hello'),'第一行 第二行')
            payload=__import__('json').loads(request.call_args.args[0].data)
            self.assertEqual(payload,{'from':'en','to':'zh-Hans','text':'Hello','html':False})
        response.read.return_value=b'{"result":""}'
        with patch('urllib.request.urlopen', return_value=response):
            with self.assertRaises(ValueError):service.mtran_piece('Hello')
