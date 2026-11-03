
"""Overseas RSS collector. Never run this on the domestic production host."""
import os,json,time,hmac,hashlib,uuid,socket,ipaddress,datetime,urllib.parse
from email.utils import parsedate_to_datetime
import requests,feedparser,trafilatura
from concurrent.futures import ThreadPoolExecutor

API=os.environ.get('NEWS_API_URL','https://codedog.online').rstrip('/')
SECRET=os.environ['NEWS_WEBHOOK_SECRET']
SESSION=requests.Session()
SESSION.headers.update({'User-Agent':'LENS-NewsReader/1.0 (RSS aggregator; respects paywalls)'})
MAX_BYTES=5*1024*1024

def public_url(url):
 u=urllib.parse.urlparse(url)
 if u.scheme!='https' or not u.hostname or u.username or u.password or (u.port and u.port!=443):
  raise ValueError('Only public HTTPS URLs are allowed')
 for row in socket.getaddrinfo(u.hostname,443,type=socket.SOCK_STREAM):
  if not ipaddress.ip_address(row[4][0]).is_global:raise ValueError('Private network target rejected')
 return url

def download(url):
 # Validate redirects independently; cap download size and total redirects.
 for _ in range(5):
  public_url(url)
  with SESSION.get(url,timeout=(10,25),allow_redirects=False,stream=True) as r:
   if r.status_code in (301,302,303,307,308):
    url=urllib.parse.urljoin(url,r.headers['Location']);continue
   r.raise_for_status()
   body=bytearray()
   for block in r.iter_content(65536):
    body.extend(block)
    if len(body)>MAX_BYTES:raise ValueError('Response too large')
   return bytes(body),url
 raise ValueError('Too many redirects')

def article(entry,source):
 try:
  url=entry.get('link','')
  raw,final_url=download(url)
  result=trafilatura.bare_extraction(raw,url=final_url,include_comments=False,include_tables=False,with_metadata=True,as_dict=True)
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
  return {'source_id':source['id'],'origin_url':final_url,'title_en':(result.get('title') or entry.get('title',''))[:1000],
          'author':result.get('author') or '', 'published_at':date,'cover_image':result.get('image') or '', 'paragraphs_en':paragraphs}
 except Exception:
  return None

def send(articles,results):
 batch={'batch_id':uuid.uuid4().hex,'articles':articles,'source_results':results}
 body=json.dumps(batch,ensure_ascii=False,separators=(',',':')).encode()
 if len(body)>2*1024*1024:
  if len(articles)<2:raise ValueError('Single article too large')
  mid=len(articles)//2;send(articles[:mid],[]);send(articles[mid:],results);return
 # Reuse the batch identifier for retries; the receiver persists it before acknowledging.
 for attempt in range(4):
  timestamp=str(int(time.time()))
  signature=hmac.new(SECRET.encode(),timestamp.encode()+b'.'+body,hashlib.sha256).hexdigest()
  try:
   r=SESSION.post(API+'/api/crawler/ingest',data=body,headers={'Content-Type':'application/json','X-Webhook-Secret':SECRET,'X-Webhook-Timestamp':timestamp,'X-Webhook-Signature':signature},timeout=(10,30))
   r.raise_for_status();return
  except Exception:
   if attempt==3:raise RuntimeError('News webhook failed; check receiver status') from None
   time.sleep(2**attempt)

def run():
 public_url(API)
 r=SESSION.get(API+'/api/crawler/tasks',params={'force_all':os.environ.get('FORCE_ALL','false')},headers={'X-Webhook-Secret':SECRET},timeout=(10,30))
 r.raise_for_status();sources=r.json()['sources']
 for source in sources:
  articles=[]
  try:
   raw,_=download(source['rss_url']);feed=feedparser.parse(raw)
   if not feed.entries:raise ValueError('Feed is empty or unreadable')
   with ThreadPoolExecutor(max_workers=3) as pool:
    articles=[a for a in pool.map(lambda e:article(e,source),feed.entries[:15]) if a]
   success=True
  except Exception:success=False
  send(articles,[{'source_id':source['id'],'success':success}])
  print('source_id=%s accepted_articles=%s feed_success=%s'%(source['id'],len(articles),success),flush=True)

if __name__=='__main__':run()
