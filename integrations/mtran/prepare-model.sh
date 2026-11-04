#!/bin/sh
# Run only on the authorized server.
set -eu
cd /opt/codedog-ranking
python3 integrations/mtran/prepare-model.py
docker volume create codedog_news-mtran-models >/dev/null
docker volume create codedog_news-mtran-config >/dev/null
docker run --rm --network none --user 0 -v /home/ubuntu/news-mtran-model-stage:/stage:ro -v codedog_news-mtran-models:/models -v codedog_news-mtran-config:/config codedog-news-mtran node --input-type=commonjs -e '
const fs=require("fs"),crypto=require("crypto"),{decompress}=require("/usr/local/lib/node_modules/mtranserver/node_modules/fzstd");
fs.mkdirSync("/models/en_zh-Hans",{recursive:true});
fs.copyFileSync("/stage/records.json","/config/records.json");
for(const r of JSON.parse(fs.readFileSync("/stage/selected.json"))){
 const bytes=decompress(fs.readFileSync("/stage/"+r.attachment.filename));
 if(!r.decompressedHash||crypto.createHash("sha256").update(bytes).digest("hex")!==r.decompressedHash)throw Error("Model hash mismatch");
 const path="/models/en_zh-Hans/"+r.attachment.filename.replace(/\.zst$/,"");
 fs.writeFileSync(path,bytes);fs.chownSync(path,1000,1000);
 console.log("Prepared verified",r.fileType,bytes.length);
}
fs.chownSync("/config",1000,1000);fs.chownSync("/models",1000,1000);
fs.chownSync("/config/records.json",1000,1000);fs.chownSync("/models/en_zh-Hans",1000,1000);
'
docker run --rm --network none --memory=1536m --memory-swap=1536m --cpus=1 --pids-limit=128 -e MT_OFFLINE=true -e MT_LOG_LEVEL=info -v codedog_news-mtran-models:/models -v codedog_news-mtran-config:/config codedog-news-mtran mtranserver --download en_zh
