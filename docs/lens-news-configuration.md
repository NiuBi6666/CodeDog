
# LENS 新闻部署与配置

## 必须的受保护配置

在生产 `/opt/codedog-ranking/.env` 中配置（不得提交 Git）：

```
NEWS_AI_BASE_URL=https://provider.example/v1
NEWS_AI_MODEL=provider-model-id
NEWS_AI_API_KEY=provider-api-key
NEWS_WEBHOOK_SECRET=at-least-32-random-characters
NEWS_GITHUB_REPO=NiuBi6666/CodeDog
NEWS_GITHUB_TOKEN=fine-grained-token-with-actions-write
NEWS_GITHUB_WORKFLOW=lens-crawl.yml
```

`NEWS_AI_BASE_URL` 到 `/v1` 即可，不要追加 `/chat/completions`。
GitHub 仓库 Actions Secret `NEWS_WEBHOOK_SECRET` 与服务器保持一致。
GitHub Actions 仓库变量 `NEWS_API_URL=https://codedog.online`。
完成模型、服务器与 Secrets 配置后，设置仓库变量 `NEWS_ENABLED=true` 启用采集。未设置时工作流跳过，不产生失败通知。
不要使用第三方免费的未授权 API；定价、免费额度与限流以提供商为准。

配置后在服务器运行 `docker compose up -d --no-deps --force-recreate backend`。
在 CodeDog `/news` 查看“服务配置”，不要把真实密钥贴入聊天或提交 Git。

## 数据闭环

Actions 每小时 17、47 分唤醒（GitHub 可能延迟），领取到期来源的 15 分钟租约。
每源最多 15 条，全文少于 150 个英文词或超过 100000 字符的报道不推送。
Webhook 校验共享密钥、5 分钟时间窗、HMAC-SHA256、2MB 限制。
批次唯一编号先持久化后确认；后台每 15 秒消费，规范化 URL 的 SHA-256 唯一约束去重。
原文每 8 段请求结构化翻译，失败退回逐段；3 次失败可在后台重试。
只有完整中英对齐且恰好 3 条摘要的文章才公开。原文、翻译失败及重试状态均保留。
同分类 24 小时内标题关键词 Jaccard ≥0.6 且至少 4 个实词相同才进行保守同题归并。
优先选择高权重媒体，同权重选正文更完整者；保留相关来源链接。
图片通过 wsrv.nl，失败提供占位；第三方代理并不保证所有地区都可用。

## 验证与限制

前台 `/lens/`；后台 `/news` 使用独立的 `news.*` 权限。
后台显示 AI 配置、调度、来源最后成功、批次及文章失败原因，不模拟抓取成功。
公开同步全局 2 分钟冷却，并有反向代理限流；GitHub 排队与翻译不承诺 20 秒内完成。
GitHub Actions 成功执行并收到第一批真实双语新闻才算端到端验收完成。
不能绕过付费墙；上线媒体需核实内容授权及对应站点抓取条款。
