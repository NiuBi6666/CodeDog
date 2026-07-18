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
- `/doc/list`：文档搜索与管理
- `/doc/create`：新建文档
- `/doc/show/:id`：公开只读文档
- `/doc/edit/:id`：编辑文档
- `/password`：修改管理员密码

旧的 `/documents/:id`、`/admin/documents` 和 `/admin/documents/:id/edit` 地址继续兼容。
文档对外使用唯一的 8 位 UUID；旧数字 ID 链接仍可访问，并会自动规范为 UUID 地址。

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

`ops/backup.sh` 使用一致性事务生成 MySQL 逻辑备份，并将数据库、`.env` 和源码快照打包到 `/opt/codedog-backups`。部署健康后必须运行：

```bash
./ops/trigger-async-backup.sh post-deploy
```
