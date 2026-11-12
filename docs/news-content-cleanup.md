# Publisher interface text cleanup

The collector scopes BBC pages to explicit text-block content before extraction.
If a video-only page has no transcript blocks, a publisher-supplied VideoObject description
may be used only when it has sufficient substantive text. No generated transcript or summary is substituted.
Metadata, navigation, buttons and known player/control modules are excluded before extraction.

Every text path (full RSS, direct HTML and reader fallback) also rejects glued control panels.
Detection requires a distinctive concatenated UI signature and at least three independent labels.
Dates in reporting and prose that merely discusses sharing controls are preserved.
A body containing only controls fails normal minimum-content validation.

The backend applies the same check at ingest, before translation and when serving historical details.
Paired original/translated paragraphs are removed at the same indices. If no original survives,
an orphan translation is never displayed. Empty content has zero translated paragraphs and zero progress.
Historical raw records remain intact for recovery; the public response is cleaned without destructive SQL.
This adds no privileged API, permission scope or source configuration changes.

Regression coverage: collector BBC HTML/video/reader tests; NewsContentCleanerTest;
NewsIntegrationTest.pollutedVideoReturnsNoOrphanTranslationOrProgress.
