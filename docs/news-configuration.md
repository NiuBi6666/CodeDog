
# CDNews 新闻部署与配置

## 后台配置正文翻译（推荐）

系统管理员登录 /news，在“正文翻译配置”可选择默认 translators 翻译（无需 API Key），或切换大模型 API 并填写兼容 OpenAI Chat Completions 的接口根地址、模型 ID 和 API Key，点击保存，再点击“测试已保存配置”。根地址不要追加 /chat/completions，模型 ID 以服务商控制台为准。

密钥使用服务器现有 AES-GCM 加密机制存入数据库，不向浏览器回传。输入框留空保留已有密钥；更换服务商地址时必须重新输入密钥。保存后立即生效，无需重启。测试发送一次小请求，可能消耗少量服务商额度。

该配置仅系统管理员可访问。数据库配置优先于环境变量；首次后台保存前兼容环境变量。服务器必须保留 CODEDOG_PASSWORD_RECOVERY_KEY，否则不能解密已保存密钥。

## 后台配置采集（推荐）

系统管理员登录 /news，在“GitHub 采集配置”填写 Token，点击“保存并自动配置”。Token 仅选择 CodeDog 仓库，并授予 Actions、Secrets、Variables 三项 Read and write。系统自动生成 Webhook 密钥、以仓库公钥加密上传、设置变量、启用工作流并提交首次采集。无需手工修改 .env 或复制 Webhook 密钥。

Token 留空保留已有值，凭证加密存储且不回显。配置任务分步骤显示，失败可修正 Token 后重试。详见 [后台自动配置采集](news-collector-admin.md)。

## 环境变量兼容与手工配置（可选）

在生产 `/opt/codedog-ranking/.env` 中配置（不得提交 Git）：

```
NEWS_AI_BASE_URL=https://provider.example/v1
NEWS_AI_MODEL=provider-model-id
NEWS_AI_API_KEY=provider-api-key
NEWS_WEBHOOK_SECRET=at-least-32-random-characters
NEWS_GITHUB_REPO=NiuBi6666/CodeDog
NEWS_GITHUB_TOKEN=fine-grained-token-with-actions-write
NEWS_GITHUB_WORKFLOW=news-crawl.yml
```

`NEWS_AI_BASE_URL` 到 `/v1` 即可，不要追加 `/chat/completions`。
GitHub 仓库 Actions Secret `NEWS_WEBHOOK_SECRET` 与服务器保持一致。
GitHub Actions 仓库变量 `NEWS_API_URL=https://codedog.online`。
完成模型、服务器与 Secrets 配置后，设置仓库变量 `NEWS_ENABLED=true` 启用采集。未设置时工作流跳过，不产生失败通知。
不要使用第三方免费的未授权 API；定价、免费额度与限流以提供商为准。

仅修改服务器环境变量后在服务器运行 `docker compose up -d --no-deps --force-recreate backend`。
后台保存模型配置无需重启。在 CodeDog `/news` 查看“服务配置”，不要把真实密钥贴入聊天或提交 Git。

## 数据闭环

Actions 每小时 17、47 分唤醒（GitHub 可能延迟），领取到期来源的 15 分钟租约。
每源最多 15 条，全文少于 150 个英文词或超过 100000 字符的报道不推送。
Webhook 校验共享密钥、5 分钟时间窗、HMAC-SHA256、2MB 限制。
批次唯一编号先持久化后确认；后台每 15 秒消费，规范化 URL 的 SHA-256 唯一约束去重。
默认翻译使用内网 Python 服务逐段处理；大模型模式每 8 段请求结构化翻译，失败退回逐段；3 次失败可在后台重试。
只有完整中英对齐的文章才公开。标题直接使用原文，不生成摘要。原文、翻译失败及重试状态均保留。
同分类 24 小时内标题关键词 Jaccard ≥0.6 且至少 4 个实词相同才进行保守同题归并。
优先选择高权重媒体，同权重选正文更完整者；保留相关来源链接。
图片通过 wsrv.nl，失败提供占位；第三方代理并不保证所有地区都可用。

## 验证与限制

前台 `/news`；后台 `/newsconfig` 使用独立的 `news.*` 权限。
后台显示 AI 配置、调度、来源最后成功、批次及文章失败原因，不模拟抓取成功。
公开同步全局 2 分钟冷却，并有反向代理限流；GitHub 排队与翻译不承诺 20 秒内完成。
GitHub Actions 成功执行并收到第一批真实双语新闻才算端到端验收完成。
不能绕过付费墙；上线媒体需核实内容授权及对应站点抓取条款。

两种方式的部署、网络与使用限制见 [正文翻译方式](news-translation-modes.md)。
