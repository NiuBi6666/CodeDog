# CDNews collection optimization — 2026-11-03

## Production behavior

- Existing GitHub pip cache is retained; collector regression tests now run in Actions.
- Authenticated POST /api/crawler/filter accepts at most 100 URLs / 256 KiB. Canonical URL hashes exclude tracking parameters and cover pending as well as published articles. No article download occurs when preflight fails.
- Overseas collection uses up to five source workers and four downloads per source, capped at twenty article downloads overall. Thread-local HTTP sessions, HTTPS/public-address checks, redirect validation, 5 MiB downloads, signed/idempotent batches and pushes every three articles remain.
- Manual refresh persists its job and returns immediately. A new internal-only news-collector service inspects the latest three entries per active source (up to 30 sources), using up to three source workers and two downloads per source.
- Fast collection takes a read-only source snapshot: it does not acquire or overwrite full-collection leases or advance last_crawled_at. Therefore inspecting three entries never suppresses a later scheduled full collection.
- Direct article extraction is preferred. Jina Reader is a bounded JSON-only fallback; error pages, access challenges, short/partial articles and invalid responses are rejected.
- On confirmed fast-source failure, GitHub is handed the job immediately while reachable sources continue independently. An unavailable gateway connection also falls back. Ambiguous HTTP/delivery failures remain uncertain instead of blindly dispatching twice.
- GitHub still performs scheduled full collection. All paths use the existing durable ingestion and URL uniqueness constraints.
- Titles commit separately as soon as translated. API mode supports up to five concurrent title workers with atomic database leases. Free translators/Bing keeps one title worker and its existing shared maximum of two provider requests, adaptive backoff and paragraph cache.
- Full body translation is still requested only when opening an article. No summaries are generated.

## Measured production run

Run 11 started 2026-11-03 12:48:40 UTC:

| Milestone | Observed elapsed time |
| --- | --- |
| Admin refresh response | 43 ms |
| Fast gateway confirmed GitHub handoff | 1,341 ms within gateway |
| Ingestion observed by 5-second poll | 25.2 s |
| First new translated title observed | 30.3 s |
| Three new titles visible; collection complete | 50.6 s |

The initial list grew from 94 to 97 visible articles. Three reachable source snapshots contained no unseen URLs and therefore required no article downloads. A separate TechCrunch probe read RSS in 1.30 s and extracted one article in 0.54 s. BBC and Guardian direct RSS probes failed; those sources require the overseas path. Fast collection finished its remaining source checks in 36.9 s without delaying the GitHub handoff.

These measurements do not establish a fixed speed multiplier or guarantee that all sources publish within 3–5 or 15 seconds.

## Verification and rollback

Remote-only suites passed: backend 208, collector 9, news frontend 8, admin frontend 66, translation service 10. Coverage includes secret authentication, canonical preflight, pending-article exclusion, source-lease isolation, early handoff before slow sources finish, independent title commits, concurrency claims, dispatch audit, and existing permission grant/revocation tests.

Anonymous /news returned 200; anonymous /api/admin/news returned 401. Authenticated source editing and manual refresh succeeded and generated audit records. Public desktop checks verified no homepage body request, one request on opening, aligned English/Chinese paragraphs and cached reloads.

Rollback images are tagged codedog-backend:rollback-fast-20261103, codedog-news-web:rollback-fast-20261103 and codedog-frontend:rollback-fast-20261103. Image references and pre-change Compose are under /home/ubuntu/cdnews-fast-rollback-20261103. No database migration or credential change was needed.
