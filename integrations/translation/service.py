import json, os, re, threading, time
from concurrent.futures import ThreadPoolExecutor, as_completed
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ENGINES = {'bing', 'alibaba', 'baidu'}
gate = threading.BoundedSemaphore(1)
_clients = threading.local()
_pool = ThreadPoolExecutor(max_workers=2, thread_name_prefix='lens-paragraph')

class RateController:
    def __init__(self, backoffs=(5, 15, 30)):
        self.condition = threading.Condition()
        self.limit = 2
        self.active = self.failures = self.successes = self.level = 0
        self.until = 0
        self.backoffs = backoffs

    def acquire(self, deadline):
        with self.condition:
            while True:
                now = time.monotonic()
                if now >= deadline:
                    raise TimeoutError()
                if now >= self.until and self.active < self.limit:
                    self.active += 1
                    return
                self.condition.wait(min(deadline-now, max(self.until-now, 0.05)))

    def finish(self, success, limited=False):
        with self.condition:
            self.active -= 1
            if success:
                self.failures = 0
                self.successes += 1
                if self.successes >= 10:
                    self.limit, self.level = 2, 0
            else:
                self.successes = 0
                self.failures += 1
                if limited or self.failures >= 2 or self.limit == 1:
                    self.limit = 1
                    self.until = max(self.until, time.monotonic()+self.backoffs[min(self.level, len(self.backoffs)-1)])
                    self.level += 1
            self.condition.notify_all()

    def status(self):
        with self.condition:
            return {'parallelism': self.limit, 'active': self.active,
                    'cooldown_seconds': max(0, round(self.until-time.monotonic()))}

rate = RateController()

def translate_piece(text, engine):
    if not hasattr(_clients, 'translator'):
        from translators.server import TranslatorsServer
        _clients.translator = TranslatorsServer()
    result = _clients.translator.translate_text(text, translator=engine, from_language='en', to_language='zh',
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

def validate(body):
    engine = body.get('engine', 'bing')
    paragraphs = body.get('paragraphs')
    if engine not in ENGINES or not isinstance(paragraphs, list) or not 1 <= len(paragraphs) <= 401:
        raise ValueError('invalid request')
    if any(not isinstance(p, str) or not p.strip() for p in paragraphs) or sum(map(len, paragraphs)) > 101000:
        raise ValueError('invalid paragraphs')
    return engine, paragraphs

def translate(body, translator=translate_piece, controller=None, on_result=None):
    engine, paragraphs = validate(body)
    deadline = time.monotonic() + 540
    controller = controller or rate
    def paragraph_work(paragraph):
        translated = []
        try:
            for part in pieces(paragraph):
                for attempt in range(3):
                    controller.acquire(deadline)
                    try:
                        result = translator(part, engine)
                        if not isinstance(result, str) or not result.strip():
                            raise ValueError('empty translation')
                    except Exception as exc:
                        limited = getattr(getattr(exc, 'response', None), 'status_code', None) == 429 or '429' in str(exc)
                        controller.finish(False, limited)
                        if attempt == 2:
                            raise
                    else:
                        controller.finish(True)
                        translated.append(result)
                        break
            return re.sub(r'[\r\n]+', ' ', ' '.join(translated)).strip() or None
        except Exception:
            return None
    # Futures are read in input order; completed paragraphs survive a neighbour's failure.
    futures = {_pool.submit(paragraph_work, p): i for i, p in enumerate(paragraphs)}
    result = [None] * len(paragraphs)
    for future in as_completed(futures):
        index = futures[future]
        result[index] = future.result()
        if on_result:
            on_result(index, result[index])
    return {'paragraphs': result, 'engine': engine}


def seal_secret(public_key, secret):
    import base64
    from nacl.public import PublicKey, SealedBox
    if not isinstance(secret, str) or not 32 <= len(secret) <= 256:
        raise ValueError('invalid secret')
    key = base64.b64decode(public_key, validate=True)
    return base64.b64encode(SealedBox(PublicKey(key)).encrypt(secret.encode())).decode()

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
        self.reply(200 if self.path == '/health' else 404, {'status': 'ready', **rate.status()} if self.path == '/health' else {})
    def do_POST(self):
        if self.path not in ('/translate', '/seal'):
            return self.reply(404, {})
        try:
            length = int(self.headers.get('Content-Length', '0'))
        except ValueError:
            return self.reply(400, {'error': 'invalid length'})
        if not 0 < length <= 2 * 1024 * 1024:
            return self.reply(413, {'error': 'invalid size'})
        if self.headers.get_content_type() != 'application/json':
            return self.reply(415, {'error': 'JSON required'})
        if self.path == '/seal':
            if length > 4096:
                return self.reply(413, {'error': 'invalid size'})
            try:
                body = json.loads(self.rfile.read(length))
                encrypted = seal_secret(body['public_key'], body['secret'])
                return self.reply(200, {'encrypted_value': encrypted})
            except Exception:
                return self.reply(400, {'error': 'invalid encryption request'})
        if not gate.acquire(blocking=False):
            return self.reply(429, {'error': 'translator busy'})
        try:
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict):
                return self.reply(400, {'error': 'invalid request'})
            if self.headers.get('Accept') == 'application/x-ndjson':
                try:
                    validate(body)
                except (ValueError, TypeError):
                    return self.reply(400, {'error': 'invalid translation request'})
                self.send_response(200)
                self.send_header('Content-Type', 'application/x-ndjson')
                self.send_header('Connection', 'close')
                self.end_headers()
                self.close_connection = True
                def emit(value):
                    try:
                        self.wfile.write((json.dumps(value, ensure_ascii=False)+'\n').encode())
                        self.wfile.flush()
                    except (BrokenPipeError, ConnectionResetError):
                        pass
                result = translate(body, on_result=lambda index,text:emit({'index':index,'text':text}))
                emit({'complete':True, 'success':all(result['paragraphs'])})
                return
            try:
                result = translate(body)
            except (ValueError, TypeError):
                return self.reply(400, {'error': 'invalid translation request or result'})
            except Exception:
                return self.reply(502, {'error': 'translation provider unavailable'})
            self.reply(200 if all(result['paragraphs']) else 502, result)
        except (json.JSONDecodeError, UnicodeDecodeError):
            self.reply(400, {'error': 'invalid JSON'})
        finally:
            gate.release()

if __name__ == '__main__':
    ThreadingHTTPServer(('0.0.0.0', 8090), Handler).serve_forever()
