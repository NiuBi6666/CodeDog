# CDNews local translation

Run builds, preparation and verification on the CodeDog server only.

- Runtime: npm mtranserver@5.0.1, CPU ONNX only; skip the optional CUDA download.
- Network: internal news-translation Docker network; no published port or public proxy.
- Resources: one CPU, 768 MiB, one model worker; engine idle lifetime 24 hours.
- First-time conversion uses a separate one-CPU/1.5-GiB container; normal inference keeps the 768-MiB limit.
- Only English → Simplified Chinese is prepared. Runtime is offline.
- Run sh integrations/mtran/prepare-model.sh to download Mozilla's official registry and model files, verify compressed/decompressed SHA-256, then convert with the pinned MTranServer. The server-side staging cache permits bounded, resumable range downloads. Keep model and configuration volumes across restarts. Never use docker compose down -v.
- Start with docker compose up -d --no-build --no-deps news-mtran. The health check translates a real phrase to warm the model.
- In /newsconfig, select MTranServer · Bing 备用 (mode=translators, engine=mtran-bing) after health and real-news checks pass. Engine mtran disables fallback; existing Bing/Alibaba/Baidu/API settings remain available.
- A failed local request opens a 30-second circuit and falls back to Bing in the combined policy. No empty result is cached as a successful translation. Requests are bounded; fallback availability still depends on Bing.
- The persistent paragraph cache includes model/rule version and policy. Existing completed articles remain readable. Pending requests reuse successful paragraphs; the detail API exposes indexed paragraphs_zh with empty slots, plus actual completed/total/stage. Full ready status still requires all paragraphs.
- News titles translate in the background; bodies translate only when requested. Public selection translation uses the same saved engine.
- Roll back by selecting Bing in the authenticated admin page; if needed restore the tagged rollback-mtran-20261104 application images. Preserve model volumes and article/cache data.
- Re-verify translation quality and bump the cache identity when intentionally changing model files or translation rules.

## Production verification (2026-11-04)

All backend (221), admin (66), public reader (8), adapter (12), and collector (14) tests passed. Browser checks covered 390px/1440px progress, out-of-order slots, reconnect and completion. A real unopened article (ID 1044, 20 paragraphs, 3864 English characters) completed through the public API in approximately 1.7 seconds; this is a sample, not a latency guarantee. With the local service stopped, Bing fallback succeeded in 4.2 seconds. Steady local-model memory was approximately 252 MiB. All services were healthy before activation.
