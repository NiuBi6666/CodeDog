"""Server-side preparation, preserving Mozilla's compressed and decompressed hashes."""
import hashlib,json,subprocess,urllib.request,time
from concurrent.futures import ThreadPoolExecutor,as_completed
from pathlib import Path
stage=Path('/home/ubuntu/news-mtran-model-stage')
stage.mkdir(exist_ok=True)
registry='https://firefox.settings.services.mozilla.com/v1/buckets/main-preview/collections/translations-models-v2/records'
records=json.loads((stage/'records.json').read_text()) if (stage/'records.json').exists() else json.load(urllib.request.urlopen(registry,timeout=30))
(stage/'records.json').write_text(json.dumps(records))
matched=[r for r in records['data'] if r['sourceLanguage']=='en' and r['targetLanguage']=='zh-Hans']
selected={}
for r in matched:
    if r['fileType'] not in selected or tuple(map(int,r['version'].split('.')))>tuple(map(int,selected[r['fileType']]['version'].split('.'))):selected[r['fileType']]=r
assert selected and any(r['fileType']=='model' for r in selected.values())
for r in selected.values():
    a=r['attachment'];name=a['filename']
    assert Path(name).name==name
    path=stage/name
    if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest()!=a['hash']:
        url='https://firefox-settings-attachments.cdn.mozilla.net/'+a['location']
        if a['size']>4*1024*1024:
            chunk=512*1024
            def part(start):
                end=min(start+chunk,a['size'])-1
                dest=stage/(a['hash']+'.part-'+str(start))
                if dest.exists() and dest.stat().st_size==end-start+1:return dest
                for attempt in range(3):
                    try:
                        request=urllib.request.Request(url,headers={'Range':f'bytes={start}-{end}'})
                        with urllib.request.urlopen(request,timeout=30) as response:
                            assert response.status==206 and response.headers.get('Content-Range')==f'bytes {start}-{end}/{a["size"]}'
                            data=response.read(chunk+1)
                        assert len(data)==end-start+1
                        dest.write_bytes(data)
                        return dest
                    except Exception:
                        if attempt==2:raise
                        time.sleep(1)
            starts=list(range(0,a['size'],chunk))
            with ThreadPoolExecutor(max_workers=8) as pool:
                done=0
                for result in as_completed([pool.submit(part,start) for start in starts]):
                    result.result();done+=1
                    print('Model chunks',done,'/',len(starts),flush=True)
            data=b''.join((stage/(a['hash']+'.part-'+str(start))).read_bytes() for start in starts)
        else:
            with urllib.request.urlopen(url,timeout=120) as response:data=response.read()
        assert len(data)==a['size'] and hashlib.sha256(data).hexdigest()==a['hash']
        path.write_bytes(data)
    print('Verified',name,a['size'],flush=True)
(stage/'selected.json').write_text(json.dumps(list(selected.values())))
