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
