# CDNews namespace migration

The active news module uses news throughout: news-web/, .github/workflows/news-crawl.yml, news-* Compose services, codedog-news-* containers, the news-translation network, news_* database tables, idx_news_* indexes and news.* credential contexts.

V138__news_namespace is an additive Java Flyway migration. It validates and decrypts existing credentials before changing any table. It preserves article IDs, text, translations, sources, queues and caches; renames nine tables and their named indexes; re-encrypts the same credential values under the new contexts; and updates the default collector workflow. The migration can resume after a partial namespace transition.

Historical V132–V137 SQL files remain byte-for-byte unchanged because deployed Flyway history validates their filenames and checksums. Old identifiers appear only in those immutable records, the one-time compatibility migration and regression tests for upgrading old installations. They are not active module or service names.

All project work and verification ran on the server. Backend 210 tests, news frontend 8, admin frontend 66, collector 9 and translator 10 passed. A production MySQL snapshot was migrated in a private rehearsal schema with no public ports: all 97 visible articles remained available, nine new tables existed, zero old tables remained, credentials were readable and news-crawl.yml stayed ready. The rehearsal container and schema were removed after verification.

Production migration passed; anonymous news viewing, authenticated source editing and a live selection translation succeeded. Previous images and Compose are retained privately under /home/ubuntu/news-namespace-rollback-20261103 and the pre-migration backup completed successfully. Database namespace and credential contexts must be reverted together when rolling back to old application images.
