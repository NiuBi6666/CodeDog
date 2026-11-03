
"""Bounded RSS collector, shared by overseas Actions and the internal fast gateway."""
import os,json,time,hmac,hashlib,uuid,socket,ipaddress,datetime,urllib.parse
from email.utils import parsedate_to_datetime
import requests,feedparser,trafilatura
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading

API=os.environ.get('NEWS_API_URL','https://codedog.online').rstrip('/')
SECRET=os.environ.get('NEWS_WEBHOOK_SECRET','')
FAST=False
ON_FAILURE=None
DEADLINE=float('inf')
_article_slots=threading.BoundedSemaphore(20)
_sessions=threading.local()
def session():
 if not hasattr(_sessions,'value'):
  _sessions.value=requests.Session()
  _sessions.value.headers.update({'User-Agent':'CDNews-NewsReader/1.0 (RSS aggregator; respects paywalls)'})
 return _sessions.value
MAX_BYTES=5*1024*1024

def public_url(url):
 u=urllib.parse.urlparse(url)
 if u.scheme!='https' or not u.hostname or u.username or u.password or (u.port and u.port!=443):
  raise ValueError('Only public HTTPS URLs are allowed')
 for row in socket.getaddrinfo(u.hostname,443,type=socket.SOCK_STREAM):
  if not ipaddress.ip_address(row[4][0]).is_global:raise ValueError('Private network target rejected')
 return url

def download(url,headers=None):
 # Validate redirects independently; cap download size and total redirects.
 for _ in range(5):
  if time.monotonic()>DEADLINE:raise TimeoutError("Collection deadline")
  public_url(url)
  with session().get(url,headers=headers,timeout=(3,8) if FAST else (5,15),allow_redirects=False,stream=True) as r:
   if r.status_code in (301,302,303,307,308):
    url=urllib.parse.urljoin(url,r.headers['Location']);continue
   r.raise_for_status()
   body=bytearray()
   for block in r.iter_content(65536):
    if time.monotonic()>DEADLINE:raise TimeoutError("Collection deadline")
    body.extend(block)
    if len(body)>MAX_BYTES:raise ValueError('Response too large')
   return bytes(body),url
 raise ValueError('Too many redirects')

def jina_article(url):
 # JSON only: never ingest a gateway error page as a news article.
 public_url(url)
 raw,_=download('https://r.jina.ai/'+url,{'Accept':'application/json','X-Return-Format':'markdown','X-Timeout':'8'})
 data=json.loads(raw)
 if data.get('code')!=200 or not isinstance(data.get('data'),dict):raise ValueError('Reader unavailable')
 data=data['data'];text=data.get('content','')
 if not text or any(x in text[:1000].lower() for x in ('access denied','captcha','verify you are human','subscribe to continue')):raise ValueError('Reader blocked')
 return {'text':text,'title':data.get('title','')}

def article(entry,source):
 with _article_slots:
  return extract_article(entry,source)

def extract_article(entry,source):
 try:
  url=entry.get('link','')
  try:
   raw,final_url=download(url)
   result=trafilatura.bare_extraction(raw,url=final_url,include_comments=False,include_tables=False,with_metadata=True,as_dict=True)
  except Exception:
   if not FAST:raise
   result=jina_article(url)
  if not result:return None
  text=result.get('text') or ''
  paragraphs=[p.strip() for p in text.split('\n') if p.strip()]
  if len(' '.join(paragraphs).split())<150 or len(text)>100000:return None
  # A word count is a first-pass filter, not a paywall bypass.
  if any(p.lower().strip().startswith(('subscribe to continue reading','already a subscriber?','sign in to continue reading')) for p in paragraphs):return None
  published=entry.get('published') or entry.get('updated')
  try:date=parsedate_to_datetime(published).astimezone(datetime.timezone.utc).isoformat().replace('+00:00','Z')
  except Exception:
   try:date=datetime.datetime.fromisoformat(result.get('date','')).replace(tzinfo=datetime.timezone.utc).isoformat().replace('+00:00','Z')
   except Exception:date=datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z')
  return {'source_id':source['id'],'origin_url':url,'title_en':(result.get('title') or entry.get('title',''))[:1000],
          'author':result.get('author') or '', 'published_at':date,'cover_image':result.get('image') or '', 'paragraphs_en':paragraphs}
 except Exception:
  return None

class WebhookError(RuntimeError):pass

def send(articles,results,completed=False):
 batch={'batch_id':uuid.uuid4().hex,'articles':articles,'source_results':results}
 run_id=os.environ.get('NEWS_RUN_ID','')
 if run_id.isdigit():batch.update(run_id=int(run_id),collection_finished=completed)
 body=json.dumps(batch,ensure_ascii=False,separators=(',',':')).encode()
 if len(body)>2*1024*1024:
  if len(articles)<2:raise ValueError('Single article too large')
  mid=len(articles)//2;send(articles[:mid],[]);send(articles[mid:],results,completed);return
 # Reuse the batch identifier for retries; the receiver persists it before acknowledging.
 for attempt in range(4):
  timestamp=str(int(time.time()))
  signature=hmac.new(SECRET.encode(),timestamp.encode()+b'.'+body,hashlib.sha256).hexdigest()
  try:
   r=session().post(API+'/api/crawler/ingest',data=body,headers={'Content-Type':'application/json','X-Webhook-Secret':SECRET,'X-Webhook-Timestamp':timestamp,'X-Webhook-Signature':signature},timeout=(10,30))
   r.raise_for_status();return
  except Exception:
   if attempt==3:raise WebhookError('News webhook failed; check receiver status') from None
   time.sleep(2**attempt)

def unseen(entries):
 # Fail closed: if the preflight is unavailable, do not redownload every old article.
 unique={}
 for e in entries:
  url=e.get('link','')
  if url.startswith('https://'):unique.setdefault(url,e)
 urls=list(unique)
 if not urls:return []
 r=session().post(API+'/api/crawler/filter',json={'urls':urls},headers={'X-Webhook-Secret':SECRET},timeout=(3,10))
 r.raise_for_status()
 allowed=set(r.json()['urls'])
 return [e for url,e in unique.items() if url in allowed]

def collect_source(source):
 pending=[]
 accepted=0
 success=False
 try:
  raw,_=download(source['rss_url']);feed=feedparser.parse(raw)
  if not feed.entries:raise ValueError('Feed is empty or unreadable')
  entries=unseen(feed.entries[:3 if FAST else 15])
  print('source_id=%s candidates=%s unseen=%s'%(source['id'],min(len(feed.entries),3 if FAST else 15),len(entries)),flush=True)
  with ThreadPoolExecutor(max_workers=2 if FAST else 4) as pool:
   futures=[pool.submit(article,e,source) for e in entries]
   for future in as_completed(futures):
    value=future.result()
    if value:
     pending.append(value);accepted+=1
     if len(pending)>=3:
      send(pending,[]);pending=[]
  success=not FAST or accepted==len(entries)
 except WebhookError:
  raise
 except Exception:
  if pending:
   send(pending,[]);pending=[]
  if FAST:
   if ON_FAILURE:ON_FAILURE()
  else:send([], [{'source_id':source['id'],'success':False}])
  print('source_id=%s feed_success=False'%source['id'],flush=True)
  return False
 if pending:send(pending,[])
 if FAST:
  if not success and ON_FAILURE:ON_FAILURE()
 else:send([], [{'source_id':source['id'],'success':success}])
 print('source_id=%s accepted_articles=%s feed_success=%s'%(source['id'],accepted,success),flush=True)
 return success

def run(internal=False):
 if not internal:public_url(API)
 r=session().get(API+'/api/crawler/tasks',params={'force_all':os.environ.get('FORCE_ALL','false'),'fast':str(FAST).lower()},headers={'X-Webhook-Secret':SECRET},timeout=(10,30))
 r.raise_for_status();sources=r.json()['sources']
 outcomes=[]
 with ThreadPoolExecutor(max_workers=3 if FAST else 5) as pool:
  for future in as_completed([pool.submit(collect_source,source) for source in sources]):
   outcomes.append(future.result())
 fallback=FAST and any(x is False for x in outcomes)
 if not fallback:send([],[],completed=True)
 return fallback

if __name__=='__main__':run()
