
"""Bounded RSS collector, shared by overseas Actions and the internal fast gateway."""
import os,json,time,hmac,hashlib,uuid,socket,ipaddress,datetime,urllib.parse,re
from email.utils import parsedate_to_datetime
import requests,feedparser,trafilatura
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading,calendar,collections

API=os.environ.get('NEWS_API_URL','https://codedog.online').rstrip('/')
SECRET=os.environ.get('NEWS_WEBHOOK_SECRET','')
FAST=False
ON_FAILURE=None
DEADLINE=float('inf')
_article_slots=threading.BoundedSemaphore(20)
_reader_slots=threading.BoundedSemaphore(2)
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
 images=[match.group(1) for match in re.finditer(r'!\[[^\]]*\]\((https://[^\s)]+)(?:\s+[^)]*)?\)',text)]
 text=re.sub(r'!\[[^\]]*\]\([^)]*\)','',text)
 return {'text':text,'title':data.get('title',''),'images':images}

def article(entry,source):
 with _article_slots:
  return extract_article(entry,source)

def enough_content(text,lang):
 return len(''.join(text.split()))>=60 if lang=='zh' else len(text.split())>=35

def publisher_full_feed(entry,source):
 # 36kr's official feed places its full HTML report in description, unlike ordinary RSS summaries.
 return (urllib.parse.urlparse(source.get('rss_url','')).hostname in ('36kr.com','www.36kr.com')
  and urllib.parse.urlparse(entry.get('link','')).hostname in ('36kr.com','www.36kr.com')
  and urllib.parse.urlparse(entry.get('link','')).path.startswith(('/p/','/newsflashes/')))

def rss_fulltext(entry,source=None):
 # Generic summaries stay excluded. Only a verified publisher full-feed format is eligible.
 from lxml import html
 candidates=list(entry.get('content',[]))
 if source and publisher_full_feed(entry,source):candidates.append({'value':entry.get('summary','')})
 for item in candidates:
  value=item.get('value','')
  if not isinstance(value,str) or not value.strip() or len(value)>MAX_BYTES:continue
  try:
   root=html.fromstring('<div>'+value+'</div>')
   for node in root.xpath('.//script|.//style|.//noscript'):node.drop_tree()
   paragraphs=[node.text_content().strip() for node in root.xpath('.//p') if node.text_content().strip()]
   text='\n'.join(paragraphs) if paragraphs else root.text_content().strip()
   if text and '\ufffd' not in text:return {'text':text,'title':entry.get('title','')}
  except Exception:continue
 return None

# Only remove a complete, recognizable footer module; dates inside reporting are meaningful.
_RECOMMENDATION_HEADING=re.compile(r'^(?:[\u3400-\u9fff]{0,12}新闻精选|相关推荐|相关阅读|推荐阅读|Related (?:articles|stories|coverage)|Recommended (?:stories|articles)|Read more|More on this story)[:：]?$',re.I)
_FOOTER_ITEM=re.compile(r'^(?:[-•*]\s*)?(?:\d{4}(?:年\d{1,2}月\d{1,2}日|[-/]\d{1,2}[-/]\d{1,2})\s+\d{1,2}:\d{2}(?::\d{2})?|\[[^\]]+\]\(https?://[^)]+\))$')
def page_chrome(text):
 # Require a glued publisher UI signature AND several independent controls.
 # A normal report mentioning sharing, dates or topics must remain intact.
 signature=re.search(r'CloseMore on this story|Shareclose panel|Subsection[A-Za-z ]{1,60}Published',text,re.I)
 controls=('share page','copy link','about sharing','related topics','read description','follow ')
 return bool(signature and sum(value in text.lower() for value in controls)>=3)

def clean_paragraphs(paragraphs):
 rows=[p.strip() for p in paragraphs if p.strip() and not page_chrome(p)]
 for i,p in enumerate(rows):
  if i and _RECOMMENDATION_HEADING.fullmatch(p):
   tail=rows[i+1:]
   if len(tail)>=2 and all(_FOOTER_ITEM.fullmatch(item) for item in tail):
    return rows[:i]
 return rows

def prepare_article_html(raw,url):
 from lxml import html
 from copy import deepcopy
 root=html.fromstring(raw)
 # A known publisher-specific body container excludes its sibling recommendation list.
 host=(urllib.parse.urlparse(url).hostname or '').lower()
 if host in ('bbc.com','www.bbc.com','bbc.co.uk','www.bbc.co.uk'):
  bodies=root.xpath('//body')
  blocks=root.xpath('//main//*[@data-component="text-block"]')
  if not blocks:
   # Video-only pages sometimes have no transcript: use a genuine supplied
   # description, never the player's navigation text.
   descriptions=[]
   def visit(value):
    if isinstance(value,dict):
     types=value.get('@type',[])
     if isinstance(types,str):types=[types]
     text=value.get('description')
     if 'VideoObject' in types and isinstance(text,str) and not page_chrome(text) and len(text.split())>=35:
      descriptions.append(text)
     for child in value.values():visit(child)
    elif isinstance(value,list):
     for child in value:visit(child)
   for script in root.xpath('//script[@type="application/ld+json"]/text()'):
    try:visit(json.loads(script))
    except (ValueError,TypeError):pass
   blocks=[]
   for text in dict.fromkeys(descriptions):
    node=html.Element('p');node.text=text;blocks.append(node)
  if bodies and blocks:
   content=html.Element('article')
   for block in blocks:content.append(deepcopy(block))
   bodies[0].clear();bodies[0].append(content)
  else:
   for node in root.xpath('//*[@data-component]'):
    if node.get('data-component') in ('metadata-block','links-block','topics','share-tools','video-block','social-embed-block'):
     if node.getparent() is not None:node.drop_tree()
 if host=='chinanews.com.cn' or host.endswith('.chinanews.com.cn'):
  bodies=root.xpath('//body')
  article=root.xpath('//*[contains(concat(" ",normalize-space(@class)," ")," left_zw ")]')
  if bodies and article:
   content=deepcopy(article[0]);bodies[0].clear();bodies[0].append(content)
 for node in root.xpath('//nav|//footer|//form|//button|//*[@role="button"]|//script[not(@type="application/ld+json")]|//style|//noscript|//*[@role="navigation"]'):
  if node.getparent() is not None:node.drop_tree()
 return html.tostring(root,encoding='unicode')

def reject(reason):
 _sessions.rejection=reason
 return None

def brief_entry(entry,source):
 return publisher_full_feed(entry,source) and urllib.parse.urlparse(entry.get('link','')).path.startswith('/newsflashes/')

def has_body(text,source,brief=False):
 return len(''.join(text.split()))>=20 if brief and source.get('lang')=='zh' else enough_content(text,source.get('lang','en'))

def feed_image(entry):
 for item in list(entry.get('media_content',[]))+list(entry.get('media_thumbnail',[]))+list(entry.get('enclosures',[])):
  url=item.get('url') or item.get('href','')
  if url.startswith('https://') and (item.get('medium')=='image' or item.get('type','').startswith('image/') or not item.get('type')):return url
 return ''

def image_identity(url):
 u=urllib.parse.urlsplit(url)
 # Responsive renditions share the same asset path; retain the largest candidate.
 host=(u.hostname or '').lower()
 if host=='i.guim.co.uk':
  match=re.search(r'/img/media/([a-f0-9]{40})/',u.path)
  if match:return host+'/'+match.group(1)
 if host=='ichef.bbci.co.uk':
  match=re.search(r'/cpsprodpb/(.+)',u.path)
  if match:return host+'/'+re.sub(r'\.(?:webp|avif)$','',match.group(1))
 return host+u.path

def srcset_candidates(value):
 # Commas are valid inside signed image URLs; only descriptor delimiters separate candidates.
 result=[];rest=value
 while rest:
  rest=rest.lstrip(' ,\t\r\n')
  if not rest:break
  match=re.search(r'\s',rest)
  if not match:
   result.append((1,rest.rstrip(',')));break
  candidate=rest[:match.start()];rest=rest[match.end():]
  if candidate.endswith(','):
   result.append((1,candidate.rstrip(',')));continue
  descriptor,separator,rest=rest.partition(',')
  try:rank=float(descriptor.strip().rstrip('wx')) if descriptor.strip() else 1
  except ValueError:rank=1
  result.append((rank,candidate))
  if not separator:break
 return result

def article_images(raw,url):
 from lxml import html
 if not raw:return []
 try:root=html.fromstring(raw)
 except Exception:return []
 scopes=root.xpath('//*[@itemprop="articleBody"]|//*[@data-gu-name="body"]|//*[contains(concat(" ",normalize-space(@class)," ")," article-body ")]')
 if not scopes:scopes=root.xpath('//article')
 if not scopes:scopes=root.xpath('//main')
 if not scopes:return []
 # Include the lead figure in the article, followed by body figures in document order.
 scope=root.xpath('//article')[0] if root.xpath('//article') else scopes[0]
 images=[];seen=set()
 bbc=(urllib.parse.urlsplit(url).hostname or '').lower() in ('bbc.com','www.bbc.com','bbc.co.uk','www.bbc.co.uk')
 if bbc:scope=(root.xpath('//main') or [scope])[0]
 for node in scope.xpath('.//img'):
  if bbc:
   # BBC .com and .co.uk serve different component markup.
   if not node.xpath('ancestor::*[@data-component="image-block" or @data-testid="image"]'):continue
   alt=node.get('alt','').lower()
   # Editorial photos can show real banners; require explicit BBC promotion wording.
   if ('banner' in alt and 'graphic for bbc news' in alt) or ('bbc news' in alt and any(x in alt for x in ('whatsapp channel','newsletter sign','follow us','subscribe to'))):continue
  parents=[node]+list(node.iterancestors())
  excluded=False
  for parent in parents:
   label=' '.join(parent.get(k,'') for k in ('class','id','data-component','data-gu-name','role')).lower()
   if parent.tag in ('aside','nav','footer') or any(x in label for x in ('related','recommend','most-viewed','mostpopular','avatar','author-image','advert','promo','onward','newsletter','video-player')):
    excluded=True;break
  if excluded:continue
  if any(parent.tag=='figure' and parent.xpath('.//video|.//iframe') for parent in parents):continue
  if not node.xpath('ancestor::figure') and not any(parent in scopes for parent in parents):continue
  if any(node.get(k,'').isdigit() and int(node.get(k))<100 for k in ('width','height')):continue
  candidates=[]
  responsive=[node.get('srcset',''),node.get('data-srcset','')]
  pictures=node.xpath('ancestor::picture')
  if pictures:responsive.extend(pictures[0].xpath('.//source/@srcset'))
  for value in responsive:candidates.extend(srcset_candidates(value))
  for value in (node.get('data-src'),node.get('src')):
   if value:candidates.append((0,value))
  for _,candidate in sorted(candidates,reverse=True):
   value=urllib.parse.urljoin(url,candidate)
   u=urllib.parse.urlsplit(value)
   if u.scheme!='https' or not u.hostname or u.username or u.password:continue
   if any(x in u.path.lower() for x in ('/avatar','/logo','/icon','/pixel','/tracking','cinemagraph--')):continue
   key=image_identity(value)
   if key not in seen:seen.add(key);images.append(value)
   break
 return images

def merge_images(*groups):
 result=[];seen=set()
 for group in groups:
  for value in group:
   if not isinstance(value,str) or not value.startswith('https://') or len(value)>8000:continue
   u=urllib.parse.urlsplit(value)
   key=image_identity(value)
   if key not in seen:seen.add(key);result.append(value)
 return result

def selected_article_images(images,result,entry,url):
 # Confirmed body figures are authoritative; metadata and reader pages can include promos.
 if images:return merge_images(images)
 if (urllib.parse.urlsplit(url).hostname or '').lower() in ('bbc.com','www.bbc.com','bbc.co.uk','www.bbc.co.uk'):
  return merge_images([feed_image(entry)])
 return merge_images(result.get('images',[]),[result.get('image'),feed_image(entry)])

def publication_date(entry,result):
 for value in (entry.get('published'),entry.get('updated'),result.get('date')):
  if not value:continue
  try:date=parsedate_to_datetime(value)
  except Exception:
   try:date=datetime.datetime.fromisoformat(value.replace('Z','+00:00'))
   except Exception:continue
  if date.tzinfo is None:date=date.replace(tzinfo=datetime.timezone.utc)
  return date.astimezone(datetime.timezone.utc).isoformat().replace('+00:00','Z')
 for field in ('published_parsed','updated_parsed'):
  if entry.get(field):return datetime.datetime.fromtimestamp(calendar.timegm(entry[field]),datetime.timezone.utc).isoformat().replace('+00:00','Z')
 return datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z')

def article_title(entry,result,url):
 title=(entry.get('title') or result.get('title') or '').strip()
 if '\ufffd' not in title:return title[:1000]
 # A replacement character cannot be decoded back into the missing text.
 # Recover only from the publisher page, never infer it from the body.
 raw,final=download(url.replace('https://36kr.com/','https://www.36kr.com/',1) if urllib.parse.urlsplit(url).hostname=='36kr.com' else url)
 from lxml import html
 try:root=html.fromstring(raw.decode('utf-8',errors='strict') if isinstance(raw,bytes) else raw)
 except UnicodeDecodeError:raise ValueError('Publisher title encoding invalid')
 candidates=root.xpath('//h1//text()')
 if candidates:candidates=[''.join(candidates).strip()]
 candidates+=root.xpath('//meta[@property="og:title"]/@content|//meta[@name="twitter:title"]/@content')
 for value in candidates:
  value=value.strip()
  if value and '\ufffd' not in value and len(value)<=1000:return value
 raise ValueError('Publisher title unavailable')

def extract_article(entry,source):
 try:
  url=entry.get('link','');brief=brief_entry(entry,source);images=[]
  result=rss_fulltext(entry,source)
  if result:
   for content in entry.get('content',[]):images.extend(article_images('<article>'+content.get('value','')+'</article>',url))
   if publisher_full_feed(entry,source):images.extend(article_images('<article>'+entry.get('summary','')+'</article>',url))
  if result:result['text']='\n'.join(clean_paragraphs((result.get('text') or '').split('\n')))
  if result and not has_body(result.get('text') or '',source,brief):result=None
  failure='empty_body'
  if result is None:
   try:
    page_url=urllib.parse.urlparse(url)._replace(netloc='www.36kr.com').geturl() if publisher_full_feed(entry,source) else url
    raw,final_url=download(page_url)
    images=article_images(raw,final_url)
    result=trafilatura.bare_extraction(prepare_article_html(raw,final_url),url=final_url,include_comments=False,include_tables=False,with_metadata=True,as_dict=True)
   except Exception as e:
    failure='deadline' if isinstance(e,TimeoutError) else 'blocked' if isinstance(e,requests.HTTPError) and e.response is not None and e.response.status_code in (401,403,429) else 'download_failed'
   if result:result['text']='\n'.join(clean_paragraphs((result.get('text') or '').split('\n')))
   if not result or not has_body(result.get('text') or '',source,brief):
    try:
     with _reader_slots:result=jina_article(url)
    except Exception:pass
  if not result:return reject(failure)
  text=result.get('text') or ''
  paragraphs=clean_paragraphs(text.split('\n'));text='\n'.join(paragraphs)
  if '\ufffd' in text:return reject('encoding_error')
  if len(text)>100000:return reject('too_large')
  if any(p.lower().strip().startswith(('subscribe to continue reading','already a subscriber?','sign in to continue reading','you have reached your article limit','verify you are human','access denied')) for p in paragraphs):return reject('restricted_content')
  if not has_body(text,source,brief):return reject('too_short')
  try:title=article_title(entry,result,url)
  except Exception:return reject('encoding_error')
  images=selected_article_images(images,result,entry,url)
  return {'source_id':source['id'],'origin_url':url,'title_en':title,
          'author':result.get('author') or '', 'published_at':publication_date(entry,result),'cover_image':(images or [''])[0], 'images':images, 'paragraphs_en':paragraphs,'content_kind':'brief' if brief else 'article'}
 except Exception:
  return reject('extraction_failed')

def inspect_article(entry,source):
 _sessions.rejection='extraction_failed'
 result=article(entry,source)
 return result,getattr(_sessions,'rejection','extraction_failed')

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
 # Check the entire feed in bounded receiver requests; fail closed on preflight errors.
 unique={}
 for e in entries:
  url=e.get('link','')
  if url.startswith('https://'):unique.setdefault(url,e)
 urls=list(unique);allowed=set()
 for offset in range(0,len(urls),100):
  r=session().post(API+'/api/crawler/filter',json={'urls':urls[offset:offset+100]},headers={'X-Webhook-Secret':SECRET},timeout=(3,10))
  r.raise_for_status();allowed.update(r.json()['urls'])
 return [e for url,e in unique.items() if url in allowed]

def collect_source(source):
 pending=[];accepted=0;reasons=collections.Counter();rejected=[]
 stats={'rss_entries':0,'checked_entries':0,'new_articles':0,'accepted_articles':0,'rejected_articles':0,'reasons':{},'rejected':[]}
 def report(ok,feed_error=None):
  stats.update(accepted_articles=accepted,rejected_articles=sum(reasons.values()),reasons=dict(reasons),rejected=rejected)
  if feed_error:stats['feed_error']=feed_error
  send([], [{'source_id':source['id'],'success':ok,'stats':stats}])
 try:
  raw,_=download(source['rss_url']);feed=feedparser.parse(raw)
  if not feed.entries:raise ValueError('Feed is empty or unreadable')
  candidates=feed.entries[:3] if FAST else feed.entries
  stats.update(rss_entries=len(feed.entries),checked_entries=len(candidates))
  entries=unseen(candidates);stats['new_articles']=len(entries)
  print('source_id=%s candidates=%s unseen=%s'%(source['id'],len(candidates),len(entries)),flush=True)
  with ThreadPoolExecutor(max_workers=2 if FAST else 4) as pool:
   futures={pool.submit(inspect_article,e,source):e for e in entries}
   for future in as_completed(futures):
    value,reason=future.result()
    if value:
     pending.append(value);accepted+=1
     if len(pending)>=3:send(pending,[]);pending=[]
    else:
     reasons[reason]+=1;entry=futures[future]
     if len(rejected)<100 and isinstance(entry,dict):rejected.append({'origin_url':entry.get('link','')[:2000],'title':entry.get('title','')[:300],'reason':reason})
  if pending:send(pending,[]);pending=[]
  success=not reasons
  if FAST:
   if not success and ON_FAILURE:ON_FAILURE()
  else:report(success)
  print(json.dumps({'source_id':source['id'],**{k:v for k,v in stats.items() if k!='rejected'},'accepted_articles':accepted,'reasons':dict(reasons),'feed_success':success},ensure_ascii=False),flush=True)
  return success
 except WebhookError:raise
 except Exception as e:
  if pending:send(pending,[])
  if FAST:
   if ON_FAILURE:ON_FAILURE()
  else:report(False,'deadline' if isinstance(e,TimeoutError) else 'feed_or_preflight_failed')
  print('source_id=%s feed_success=False'%source['id'],flush=True)
  return False

def run(internal=False):
 global DEADLINE
 if not FAST:DEADLINE=time.monotonic()+1200
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

def refill_article(url):
 public_url(url)
 response=session().get(API+'/api/crawler/tasks',params={'fast':'true'},headers={'X-Webhook-Secret':SECRET},timeout=(10,30))
 response.raise_for_status()
 host=urllib.parse.urlparse(url).hostname
 allowed_hosts={host}
 if host in ('bbc.com','www.bbc.com','bbc.co.uk','www.bbc.co.uk'):allowed_hosts.add('feeds.bbci.co.uk')
 source=next((s for s in response.json()['sources'] if urllib.parse.urlparse(s['rss_url']).hostname in allowed_hosts),None)
 if source is None:raise ValueError('Article host must match an active configured source')
 result=extract_article({'link':url},source)
 if not result or not result['images']:
  try:
   from lxml import html
   raw,final=download(url);root=html.fromstring(raw)
   print('Image scope diagnostic: '+json.dumps({'main':len(root.xpath('//main')),'article':len(root.xpath('//article')),'images':[{'src':n.get('src'),'alt':n.get('alt'),'parents':[{'tag':p.tag,**{k:p.get(k) for k in ('class','id','data-testid','data-component') if p.get(k)}} for p in n.iterancestors()]} for n in root.xpath('//main//img')]}),flush=True)
  except Exception as error:print('Image scope unavailable: '+type(error).__name__,flush=True)
  raise ValueError('No article images extracted')
 send([result],[])
 print('Article image refill submitted: %s images'%len(result['images']),flush=True)
 print(json.dumps({'source_id':source['id'],'images':result['images']},ensure_ascii=False),flush=True)

if __name__=='__main__':
 target=os.environ.get('NEWS_ARTICLE_URL','').strip()
 if target:refill_article(target)
 else:run()
