# 后台自动配置新闻采集

系统管理员登录 /news，填写 GitHub 仓库、工作流、公开 API 地址和 fine-grained Token，点击“保存并自动配置”。仅系统管理员可读写或启动配置；Token 与 Webhook 密钥使用服务器已有 AES-GCM 密钥加密保存，不回传浏览器，也不写入日志。留空保留 Token；更换仓库需重新输入。未保存后台配置时兼容原 NEWS_GITHUB_* / NEWS_WEBHOOK_SECRET 环境变量。

Token 仅选择目标仓库，授予 Actions、Secrets、Variables 的 Read and write。之前只有 Actions 写权限的 Token 需要补齐权限。建议设置有效期；若组织需要审批须先批准。Metadata 为 GitHub 自动要求的只读权限。

自动任务检查仓库内已存在的 news-crawl.yml，以 GitHub 仓库公钥和 PyNaCl SealedBox 加密 Webhook 密钥后写入 NEWS_WEBHOOK_SECRET，更新 NEWS_API_URL，启用工作流并设置 NEWS_ENABLED=true，最后 dispatch main 分支并记录采集请求。程序不更改协作者、分支规则、仓库可见性或其他 Secrets。更换仓库生成新的采集密钥。

配置任务状态持久化，并使用 5 分钟处理租约；重启后可恢复。过程分步骤展示。GitHub 各调用非事务性，权限不足可能造成部分步骤已成功，修正 Token 后可幂等重试。失败请求不回传上游原始响应和凭证。成功仅表示完成配置并提交工作流，不代表新闻已抓取翻译成功。后台抓取记录继续展示真实入库状态。

GitHub Secret 密文由内网翻译服务 /seal 接口使用 PyNaCl 生成；该接口只做本地公钥加密，不向翻译提供方发送凭证、不发布宿主机端口。只在 /news 的保存动作后自动生成本应用的 Webhook 密钥，无需人工复制密钥或改动 .env，也无需重启容器。
