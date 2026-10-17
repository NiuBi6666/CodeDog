# 积分系统分批完善

第一批：积分流水、对账及人工台账修复、导入预览确认与冲正撤销。

- 历史余额以 OPENING 记录入账，不伪造历史课程时间。
- 原课程结果、人工调整和兑换记录继续作为业务来源；独立流水用于核对，修复台账不改变可用积分。
- 导入确认与撤销均为事务；余额不足、版本冲突或部分拒绝时整批回滚。
- 每位老师的积分写入按老师行、学生行统一顺序加锁，防止兑换、调整及导入相互覆盖。
- 新增权限在权限配置页面动态展示；最高管理员自动拥有，普通教师需单独授权确认、撤销及台账修复。
- /api/rankings/admin/imports/xlsx 保留旧接口兼容；管理页面使用 operations/import-previews 的预览确认流程。
- 不能安全撤销缺少变更快照的历史批次。
- 每天北京时间02:15自动对账；隔离环境通过 CODEDOG_MAINTENANCE_ENABLED=false 关闭该任务。

验证脚本：ops/qa/points-operations-mysql.py 仅访问本机回环端口18090的隔离后端，禁止改成生产地址；浏览器脚本使用模拟API，不创建真实数据。

后续批次：账号找回及兑换取消；教师交接及手机号共用；月榜、库存规则及消息；隔离恢复和整体验收。

## 第二批

- 取消申请仅限未发放订单；审批时原子退款及返库存，重复审批拒绝。删除奖品保留历史数据并保持下架。
- 学生兑换使用 Idempotency-Key，同一请求只扣一次积分；旧客户端接口保留兼容。
- 账号找回统一返回受理信息；教师核实后仅最高管理员可重置。重置及改密递增会话版本，旧会话失效。
- 管理界面增加“取消兑换与账号找回”，独立配置查看、核实、审批权限。
- 学生端登录页提供找回入口，未发放订单提供取消申请。


## Batch 3 — ownership and handover

Current ownership is independent from historical lesson/order owners. Duplicate historical relationships are marked CONFLICT and require highest-administrator resolution; balances are never silently merged. Transfer previews expire in 30 minutes and recheck ownership version, balance and destination before confirmation. Transfers use ordered teacher locks and paired ledger entries. Account identity/password is retained; the recovery ciphertext context is rewrapped and sessions invalidated. Offboarding disables the teacher and revokes devices. Old-teacher imports, adjustments and reversals cannot reclaim students.

Students retain historical order visibility through explicit transfer records. Original teachers process those orders; later refunds are forwarded atomically to the student's current owner. Shared phone numbers are allowed; ambiguous login requires the student ID without disclosing other accounts. Completeness distinguishes missing phones, unopened accounts, rejected imports and missing expected lesson data. Missing data is not treated as incomplete coursework.

Batch 3 validation: 127 backend tests and 69 frontend tests passed; isolated MySQL transfer/refund/shared-phone/offboarding suite and desktop/mobile browser flows passed. Production checks are read-only.


## Batch 4 — monthly history, prize limits, inbox

Monthly rankings sum only normal course results in the Asia/Shanghai calendar month of the verified lesson occurrence date. Neither balance adjustments nor redemptions/refunds/transfers affect the monthly score. Unknown dates remain excluded and visible as missing; teachers with the separate date permission may correct them, after which historic boards are recomputed from source data. Student responses expose only the first ten entries and their own entry, even for tied scores.

Prize rules independently configure stock-warning threshold, per-student lifetime quantity (zero means unlimited), start/end times and fulfillment instructions. The transaction locks the prize before enforcing all restrictions. CANCELLED orders do not consume the quantity allowance. Low stock, pending requests and ledger discrepancies are visible in the teacher alert panel. Both interfaces have paginated in-app inboxes with unread/read state and fixed internal business navigation. Notifications use business idempotency keys and are written in the business transaction; no external messaging service is contacted.

Validation: 133 backend and 69 frontend tests passed. Isolated real MySQL verified concurrent quantity enforcement, cancellation releasing allowance, date correction and exclusion of manual balance changes, inbox tenant/student boundaries, alert state, and zero source/ledger differences. Teacher/student desktop and mobile browser flows passed.


## Batch 5 — isolated recovery and acceptance

`CODEDOG_MAINTENANCE_ENABLED=false` disables registration of ALL scheduled jobs, including audit cleanup and announcement SSE refresh. Production defaults remain enabled. The recovery-drill report is a non-delegable highest-administrator GET endpoint; no application user can execute server commands through it.

Run `python3 ops/qa/recovery-drill.py` on the authorized server. It restores the latest authorized archive to uniquely named private MySQL/backend containers on an internal Docker network, publishes no host ports and uses an ephemeral client inside that same internal network, disables scheduling/legacy import/CRM cookies, and verifies account associations, exact reward image bytes, source files/configuration, order foreign references, ledger balances, migrations, administrator login and anonymous denials. No production business data is modified. The temporary recovered configuration copy and containers are removed; the original backup and private JSON evidence are retained. The report states backup start/end bounds rather than inventing an exact database-snapshot timestamp.

After production has migration V125, `python3 ops/qa/recovery-drill.py publish /home/ubuntu/codedog-recovery-drill-.../report.json` inserts a successful report and its operations audit event. Only server operators and the highest administrator can publish/read this evidence; it is not exposed to ordinary teachers. No password, token or encryption key is included in the report.
