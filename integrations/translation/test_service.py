import unittest
from service import translate, pieces

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
        self.assertEqual(''.join(t for t, _ in calls), ''.join(source))
        self.assertTrue(all(len(t) <= 1000 for t, _ in calls))
    def test_invalid_input_never_calls_provider(self):
        def forbidden(*args):
            self.fail('provider called for invalid input')
        for body in [{'engine':'unknown','paragraphs':['text']}, {'paragraphs':[]},
                     {'paragraphs':['']}, {'paragraphs':['a'*100001]}, {'paragraphs':[1]}]:
            with self.assertRaises(ValueError):
                translate(body, forbidden)
    def test_empty_result_is_not_published(self):
        with self.assertRaises(ValueError):
            translate({'paragraphs':['Hello']}, lambda *_: '')

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
