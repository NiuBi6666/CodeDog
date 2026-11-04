# CodeDog maintenance rules

- Keep this project independent from the CodeMao application. The only shared resource is the `codemao_default` Docker network used by the public ACME challenge proxy.
- Never commit `.env`, MySQL data, legacy SQLite databases, migration archives, SSH keys, or backup checksums.
- Keep administrator credentials outside Git. Require `ADMIN_PASSWORD` from the untracked `.env`; deployment automation must never overwrite an existing administrator password.
- Run the complete test suite before deployment.
- After deployment, wait for the container to become healthy and verify both anonymous viewing and authenticated editing.
- After every healthy deployment, run `./ops/trigger-async-backup.sh post-deploy` and confirm `codedog-backup.service` succeeds.
- Migration archives contain the administrator credential configuration, documents, and student data. Keep them off public storage.

## Permission and audit requirements

- Every new management feature and every behavior change must include permission design in the same change. Split page access, data access, create, update, delete, import, export, publish, status, fulfillment, and other distinct operations whenever they can be granted independently.
- Enforce every permission on the backend API. Frontend route guards, hidden controls, and disabled buttons are usability measures and must never be the only authorization boundary.
- Register management APIs explicitly in the security allowlist. Unregistered management APIs must fail closed. Keep public, student-session, and extension-token APIs on their dedicated authentication boundaries.
- Update the permission catalog, permission-management UI, page navigation, API rules, and automated tests together. Tests must cover denial without permission, success after grant, and immediate denial after revocation in the same session.
- `Liam` remains the non-delegable system administrator with all permissions. Password recovery and password reset stay system-administrator-only and must not appear in the configurable permission catalog.
- Record an audit event for every privileged API operation, including reads, searches, exports, shares, creates, updates, deletes, imports, publishes, status changes, permission changes, credential operations, and fulfillment actions.
- Audit events must use the most specific action name available and identify the actor, module, target type, target ID or stable key, affected owner, outcome, timestamp, source IP, and request correlation identifier where available.
- For mutations, record a field-level before/after change summary and batch counts or per-item failures where applicable. Record denied and failed operations with a sanitized reason; ensure one logical operation produces one authoritative audit event.
- Never write passwords, password recovery ciphertext, session IDs, CSRF tokens, device tokens, cookies, authorization headers, encryption keys, or complete sensitive personal data to audit logs. Mask identifiers when full values are not necessary for investigation.
- Permission and audit coverage are release-blocking acceptance criteria. Do not describe a feature as complete until its authorization and audit tests pass on the remote server.

## UI copy: minimal by default

- Applies to all new or modified user-facing pages and components in this repository.
- Do not add unsolicited page subtitles, introductory paragraphs, how-to prose, feature explanations, implementation details, storage/encryption descriptions, permanent success narratives, or footer instructions.
- Prefer a clear field label, concise action label, and one status indicator. Do not repeat status already shown by a badge or action feedback.
- Keep necessary accessible labels, loading/empty/error states, validation messages, and short input placeholders that affect correct entry. Never hide errors or remove authorization controls to reduce copy.
- Add explanatory copy only when explicitly requested by the user or strictly needed to complete a concrete operation; keep it short and contextual. Put reference material and setup tutorials in docs instead of the default interface.
- Remove unwanted copy from component source; do not hide it with CSS, shrink it, or move it into tooltips merely to bypass this rule.
- Before delivery, review visible text at desktop and mobile sizes and remove copy that does not help identify data, enter a value, choose an action, or understand a necessary current state.

## News category scope
- News uses only the seven primary categories: 国际、军事、科技、财经、教育、文娱、生活.
- Do not reintroduce secondary categories, topic chips, subcategory filters, or source subcategory configuration unless the user explicitly requests the feature again.
