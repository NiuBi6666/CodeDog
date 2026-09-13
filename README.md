# CodeDog

前后端分离的公开文档与学生查询管理系统。

- 前端：Vue 3、Vue Router、Vite
- 后端：Java 21、Spring Boot、Spring Security、Spring Data JPA、Flyway
- 数据库：MySQL 8.4
- 部署：Docker Compose、Nginx、HTTPS

## 页面路径

- 生产地址：`https://codedog.online`、`https://www.codedog.online`
- `/`：跳转到管理首页
- `/index`：管理首页
- `/student/query`：按姓名查询学生 ID，或按 ID 查询姓名
- `/class/progress`：按营期、班级和课次查询每道编程题的完成情况
- `/questionnaire`：问卷与作业管理（基于 TDuck，CodeDog 登录后通过单点登录直接进入）
- `/doc/list`：文档搜索与管理
- `/doc/create`：新建文档
- `/doc/show/:id`：公开只读文档
- `/doc/edit/:id`：编辑文档
- `/logs`：查询登录、文档、学生查询和账户安全操作日志
- `/password`：修改管理员密码

旧的 `/documents/:id`、`/admin/documents` 和 `/admin/documents/:id/edit` 地址继续兼容。
文档对外使用唯一的 8 位 UUID；旧数字 ID 链接仍可访问，并会自动规范为 UUID 地址。
编辑器支持图片、表格、代码块和 KaTeX 数学公式，并尽量保留从钉钉文档粘贴的上下标、字号、缩进、行高与列表格式。

## 课堂完成情况

课堂完成情况由后端只读调用编程猫老师端接口。凭据缺失或过期时，页面会要求管理员输入当前老师账号的 Cookie；验证成功后仅保存在当前后台登录会话，退出登录后自动清除，不写入数据库或浏览器本地存储。

也可以在生产环境未提交的 `.env` 中设置默认 Cookie：

```dotenv
CODEMAO_SESSION_COOKIE=your-current-codemao-cookie
```

该值只作为服务端上游请求头使用，不会返回给前端，也不能提交到 Git。默认 Cookie 过期后可以直接在页面弹窗中输入新 Cookie，或更新 `.env` 并重建后端容器。

## 本地开发

后端需要 Java 21、Maven 和 MySQL：

```bash
cd backend
mvn spring-boot:run
```

前端需要 Node.js 22：

```bash
cd frontend
npm install
npm run dev
```

也可以在项目根目录创建 `.env` 后直接启动完整环境：

```bash
cp .env.example .env
docker compose up -d --build
```

首次初始化前必须在未提交的 `.env` 中设置 `ADMIN_USERNAME` 和高强度的 `ADMIN_PASSWORD`。生产密码不得写入源码、示例配置或 Git 历史；已有管理员记录不会在重启或重新部署时被覆盖。

## 旧数据迁移

首次从 Python/SQLite 版本升级时，把旧版公开文档数据库放到 `migration/public_doc.sqlite3`，把 CodeMao 学生数据库放到 `migration/codemao.sqlite3`，并设置：

```dotenv
LEGACY_IMPORT_ENABLED=true
```

Spring Boot 会在 MySQL 表为空时导入文档与学生数据。导入具有幂等保护，后续重启不会重复写入。

## 备份

ops/backup.sh 使用一致性事务备份 CodeDog 与 TDuck 数据库、TDuck 上传文件、两套环境配置和源码快照，并打包到 /opt/codedog-backups。

```bash
./ops/trigger-async-backup.sh post-deploy
```

## TDuck 集成源码

定制补丁与部署清单位于 integrations/tduck，实际运行环境配置不进入 Git。
后端基线：TDuckCloud/tduck-survey-form，提交 ea7f0fae7cb0fd998a3284c11addce689350cd69。
前端基线：TDuckCloud/tduck-front，提交 257932566963fb0a3e70e9d40c837689bc2878c0。

## Student ID service

The CodeMao student ID application is exposed through this HTTPS gateway at:

- https://codedog.online/student-id/

The frontend container joins the external codemao_default network and proxies this path to the CodeMao application container.


## 成绩管理与家长查询

管理员登录后进入左侧“成绩管理”（/exams），填写考试类型或名称并上传 .xlsx / .xls。支持选择工作表、表头行、姓名列及 1–20 列成绩，成绩列名称可修改；上传前显示前五行预览。文件最大 10 MB，每场最多 10000 名学员。同一表内的重名、姓名缺失和成绩错误值会阻止整表导入。

默认赛考明细模板读取 B 列用户姓名、O 列提交时间、Q 列总得分，以及 Q 后所有有数据的列（不限题目得分表头）。整列为空的明细不导入，学员某项明细为空则不展示该项，0 属于有数据；题目得分按题号排列。

全量与简单模板按表头自动识别，无需手动选列：两者均要求唯一的“用户姓名”（或“姓名”）、“提交时间”和“总得分”（或“总成绩”）列。全量包含“第N题得分”列，展示总成绩及所有题目得分，按题号排序，支持超过 20 个成绩项目；简单模板只展示总成绩。模板映射由后端重新确认，用户 ID、教师姓名、提交时间、报名信息等不进入公开结果。提交时间为 NaT（忽略大小写和首尾空格）时仅显示“未参赛”；已提交的总分与单题 0 分正常显示 0，空白成绩为“暂无成绩”。原有自定义导入及既有考试保留原来的 0 分显示规则。

每次成功上传都创建新的考试和独立的 8 位查询路径 /exam/<8位标识>。标识区分大小写，同时包含大写字母、小写字母和数字（已有 8 位及 32 位链接继续有效），同名考试也有独立链接。家长无需登录，按完整姓名查询时仅返回该场考试对应学员的成绩；旧版自定义成绩中 0 显示“未参考”，空白显示“暂无成绩”。不同考试的数据按考试 ID 隔离，公开接口不提供名单、导出或批量查询。每个来源 IP 每分钟最多查询 30 次。

后台支持复制链接、查看、暂停和恢复查询。管理员可在考试列表点击“修改后缀”并保存；前缀 https://codedog.online/exam/ 固定不可修改。保存后复制和查看使用新链接，历史链接仍归属原考试，不得被其他考试占用；重复后缀及过期编辑会返回 409。暂停后公开信息和查询接口均返回 410；恢复后仍使用原链接。管理 API 复用管理员验证和 CSRF 保护，姓名查询 API 保持 CSRF 保护。原 Excel 不保存，数据库只保存选定的姓名、成绩及考试配置；成绩随现有 MySQL 备份归档，不得提交到 GitHub。

数据库迁移：V105__exam_score_queries.sql（新增 exam_sessions / exam_scores）；V106__custom_exam_query_codes.sql（新增规范后缀与区分大小写的历史别名表，启动时为旧考试补齐混合字符后缀）。V107__exam_template_results.sql 保存展示模式和未参赛标记。功能测试：ExamIntegrationTest、examImport.test.js。

## 代码目录约定

后端按职责集中管理：controller 目录放置全部 HTTP 控制器，service 目录放置全部业务服务及计算与导入辅助类，dao 目录放置全部数据访问接口，config 目录放置 Spring 配置，model 目录放置实体和接口数据模型；只有确有必要时才在这些目录下增加二级包。前端可直接发布的静态文件统一放在 frontend/public 目录，由构建产物复制到站点根目录，确保 favicon、排行榜页面和验证文件保持原有 URL。
