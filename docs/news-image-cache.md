# News image delivery

News and newspaper galleries use same-origin /news/media endpoints served by news-web.
Preview images request WebP at up to 1200px without cropping or upscaling.
The CNBC route uses a fixed CNBC CDN upstream, preserving its original URL parameters.
Zoom requests a separate original-size representation; the preview remains visible while it loads.

Nginx caches successful responses in the news-image-cache named volume, with a 300MB maximum
and seven-day inactivity expiry. Errors are not cached. Cache locking coalesces concurrent misses.
Stale images may serve during upstream outages. Browser caching lasts one day.
Cold misses still depend on overseas upstream availability; this is demand caching, not ingestion-time archiving.
Original publisher URLs remain unchanged in the database.

Only the fixed wsrv.nl and image.cnbcfm.com upstreams are used. Request cookies and
authorization are not forwarded. Image responses carry nosniff and a sandboxed content policy.
This adds public image delivery only; no management API or permission changes are introduced.

The shared loader caps concurrent preloads at three and retains up to 80 promises.
Hover/focus prefetch waits 180ms, allows at most eight starts per minute, and skips slow or save-data connections.
Adjacent slides preload after the current image loads. Failed images collapse to a small retry state.
No new object storage, paid CDN or external credentials are required.

Validation: news-images.test.js; ops/qa/news-gallery.cjs (mock image routes);
ops/qa/news-cache-live.cjs (real production articles).
