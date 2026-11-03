"""Internal-only fast collection gateway. No published host port or caller-provided URLs."""
import json,os,time,threading
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import crawler
gate=threading.Lock()
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def reply(self,status,data):
  raw=json.dumps(data).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
 def do_GET(self):
  self.reply(200 if self.path=='/health' else 404,{'ready':True})
 def do_POST(self):
  if self.path!='/collect':return self.reply(404,{})
  try:
   length=int(self.headers.get('Content-Length','0'))
   if not 0<length<=8192:raise ValueError()
   body=json.loads(self.rfile.read(length));run=int(body['run_id']);secret=body['secret']
   if run<1 or not isinstance(secret,str) or not 32<=len(secret)<=4096:raise ValueError()
  except Exception:return self.reply(400,{'completed':False})
  if not gate.acquire(False):return self.reply(409,{'completed':False})
  started=time.monotonic()
  try:
   crawler.SECRET=secret;crawler.FAST=True;crawler.DEADLINE=time.monotonic()+100
   os.environ['NEWS_RUN_ID']=str(run);os.environ['FORCE_ALL']='true'
   replied=threading.Event();reply_lock=threading.Lock()
   def handoff():
    # A confirmed failure starts GitHub immediately while reachable sources continue.
    with reply_lock:
     if not replied.is_set():
      self.reply(200,{'acknowledged':True,'fallback':True});replied.set()
   crawler.ON_FAILURE=handoff
   fallback=crawler.run(internal=True)
   with reply_lock:
    if not replied.is_set():self.reply(200,{'acknowledged':True,'fallback':fallback});replied.set()
   print('fast_run=%s duration_ms=%s fallback=%s'%(run,int((time.monotonic()-started)*1000),fallback),flush=True)
  except Exception:
   # Unknown delivery is not automatically dispatched twice.
   if 'replied' not in locals() or not replied.is_set():self.reply(502,{'acknowledged':False})
  finally:
   crawler.SECRET='';crawler.ON_FAILURE=None;gate.release()
if __name__=='__main__':ThreadingHTTPServer(('0.0.0.0',8091),Handler).serve_forever()
