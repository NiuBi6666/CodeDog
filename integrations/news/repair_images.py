"""Repair existing Guardian signatures using publisher URLs, without rewriting article text."""
import os,json,threading,urllib.parse
from concurrent.futures import ThreadPoolExecutor,as_completed
import requests
from lxml import html
import crawler
def restore_images(stored,candidates,valid):
 by_asset={}
 for image in candidates:
  by_asset.setdefault(crawler.image_identity(image),[]).append(image)
 restored=[];changed=False;unresolved=0
 for old in stored:
  if urllib.parse.urlsplit(old).hostname!='i.guim.co.uk' or valid(old):
   restored.append(old);continue
  replacement=next((value for value in by_asset.get(crawler.image_identity(old),[]) if valid(value)),None)
  restored.append(replacement or old);changed|=bool(replacement and replacement!=old);unresolved+=replacement is None
 return restored,changed,unresolved
def run():
 response=crawler.session().get(crawler.API+'/api/crawler/tasks',params={'fast':'true'},headers={'X-Webhook-Secret':crawler.SECRET},timeout=30);response.raise_for_status()
 source_ids={s['id'] for s in response.json()['sources']}
 rows=[];cursor=''
 while True:
  r=crawler.session().get(crawler.API+'/api/news',params={'cursor':cursor} if cursor else {},timeout=30);r.raise_for_status();page=r.json()
  rows.extend(a for a in page['articles'] if urllib.parse.urlsplit(a.get('cover_image','')).hostname=='i.guim.co.uk')
  if not page.get('has_more'):break
  cursor=page.get('next_cursor')
  if not cursor:break
 print('IMAGE_REPAIR_CANDIDATES',len(rows),flush=True)
 def repair(row):
  try:
   a=crawler.session().get(crawler.API+'/api/news/'+str(row['id']),timeout=30);a.raise_for_status();a=a.json()
   if a.get('source_id') not in source_ids:return 'inactive_source'
   origin=a['origin_url']
   if urllib.parse.urlsplit(origin).hostname not in ('www.theguardian.com','theguardian.com'):return 'other_publisher'
   current=a.get('images') or [a['cover_image']]
   cache={}
   def valid(url):
    if url not in cache:
     try:
      with crawler.session().get(url,stream=True,timeout=(5,12)) as image:
       cache[url]=image.status_code==200 and image.headers.get('Content-Type','').startswith('image/')
     except requests.RequestException:cache[url]=False
    return cache[url]
   if all(valid(u) for u in current):return 'already_valid'
   raw,final=crawler.download(origin)
   root=html.fromstring(raw)
   candidates=crawler.article_images(raw,final)
   candidates+=root.xpath('//meta[@property="og:image"]/@content')
   images,changed,unresolved=restore_images(current,candidates,valid)
   if not changed:return 'unresolved'
   paragraphs=(a.get('content_original') or a.get('content_en') or '').split('\n\n')
   payload={'source_id':a['source_id'],'origin_url':origin,'title_en':a.get('title_original') or a['title_en'],'author':a.get('author') or '','published_at':a['published_at'],'paragraphs_en':paragraphs,'cover_image':images[0],'images':images,'content_kind':a.get('content_kind') or 'article'}
   crawler.send([payload],[])
   print('IMAGE_REPAIR_SUBMITTED',a['id'],'images',len(images),'unresolved',unresolved,flush=True)
   return 'partial' if unresolved else 'submitted'
  except Exception as error:
   print('IMAGE_REPAIR_UNRESOLVED',row['id'],type(error).__name__,flush=True);return 'unresolved'
 counts={}
 with ThreadPoolExecutor(max_workers=4) as pool:
  for future in as_completed([pool.submit(repair,a) for a in rows]):
   result=future.result();counts[result]=counts.get(result,0)+1
 print('IMAGE_REPAIR_RESULT',json.dumps(counts,sort_keys=True),flush=True)
 if counts.get('unresolved') or counts.get('partial'):raise RuntimeError('Some original images could not be recovered; existing links retained')
if __name__=='__main__':run()
