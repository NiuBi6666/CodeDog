import json, os, re, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ENGINES = {'bing', 'alibaba', 'baidu'}
gate = threading.BoundedSemaphore(1)
_translator = None

def translate_piece(text, engine):
    global _translator
    if _translator is None:
        import translators
        _translator = translators
    result = _translator.translate_text(text, translator=engine, from_language='en', to_language='zh',
        timeout=12, sleep_seconds=0.3, if_ignore_empty_query=False)
    if not isinstance(result, str) or not result.strip():
        raise ValueError('empty translation')
    return re.sub(r'[\r\n]+', ' ', result).strip()

def pieces(text, limit=1000):
    # Prefer sentence/word boundaries; keep all source text and original paragraph grouping.
    while len(text) > limit:
        end = max(text.rfind('. ', 0, limit), text.rfind(' ', 0, limit))
        end = end + 1 if end > limit // 2 else limit
        yield text[:end]
        text = text[end:]
    if text:
        yield text

def translate(body, translator=translate_piece):
    engine = body.get('engine', 'bing')
    paragraphs = body.get('paragraphs')
    if engine not in ENGINES or not isinstance(paragraphs, list) or not 1 <= len(paragraphs) <= 400:
        raise ValueError('invalid request')
    if any(not isinstance(p, str) or not p.strip() for p in paragraphs) or sum(map(len, paragraphs)) > 100000:
        raise ValueError('invalid paragraphs')
    deadline = time.monotonic() + 540
    result = []
    for paragraph in paragraphs:
        translated = []
        for part in pieces(paragraph):
            if time.monotonic() > deadline:
                raise TimeoutError()
            translated.append(translator(part, engine))
        text = re.sub(r'[\r\n]+', ' ', ' '.join(translated)).strip()
        if not text:
            raise ValueError('empty translation')
        result.append(text)
    return {'paragraphs': result, 'engine': engine}

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # Do not log article bodies, query strings, or provider errors.
    def reply(self, code, data):
        payload = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        try:
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError):
            pass
    def do_GET(self):
        self.reply(200 if self.path == '/health' else 404, {'status': 'ready'} if self.path == '/health' else {})
    def do_POST(self):
        if self.path != '/translate':
            return self.reply(404, {})
        try:
            length = int(self.headers.get('Content-Length', '0'))
        except ValueError:
            return self.reply(400, {'error': 'invalid length'})
        if not 0 < length <= 2 * 1024 * 1024:
            return self.reply(413, {'error': 'invalid size'})
        if self.headers.get_content_type() != 'application/json':
            return self.reply(415, {'error': 'JSON required'})
        if not gate.acquire(blocking=False):
            return self.reply(429, {'error': 'translator busy'})
        try:
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict):
                return self.reply(400, {'error': 'invalid request'})
            try:
                result = translate(body)
            except (ValueError, TypeError):
                return self.reply(400, {'error': 'invalid translation request or result'})
            except Exception:
                return self.reply(502, {'error': 'translation provider unavailable'})
            self.reply(200, result)
        except (json.JSONDecodeError, UnicodeDecodeError):
            self.reply(400, {'error': 'invalid JSON'})
        finally:
            gate.release()

if __name__ == '__main__':
    ThreadingHTTPServer(('0.0.0.0', 8090), Handler).serve_forever()
