# 小组协作与在线演示部署

这是模拟交易的毕业设计系统。代码托管、网页发布、后端运行是三个步骤，GitHub Pages 不能运行 Java 后端或存放账号数据库。

## 本地运行

Java 17+、Maven、Node 22。在 server 运行 `mvn spring-boot:run`，在 web 运行 `npm ci`、`npm run dev`。本地会创建演示账号 user/123456、admin/admin123，不能将此模式直接暴露公网。新注册密码为 8–64 字符。

## 方案 A：同一服务器运行前后端（推荐小组演示）

1. 安装 Docker Compose；将 server/.env.example 复制成 server/.env，设置不同的高强度数据库密码和至少 12 位管理员密码。密钥只填服务端。不要上传 .env。
2. 在 server 目录运行 `docker compose up -d --build`。会启动 MySQL、Java 后端和网页。
3. 网页仅绑定服务器本机 127.0.0.1:8088。由云平台 HTTPS 入口或服务器反向代理转发，禁止直接开放数据库端口。正式对外访问前需要配置 HTTPS；本配置不会自动申请域名/证书。
4. MySQL 与头像使用独立命名卷；更新容器会保留数据。不要运行 `docker compose down -v`，它会删除卷。定期另行备份数据库与头像卷；卷不是备份。

## 方案 B：GitHub Pages + 独立 HTTPS 后端

先取得后端 HTTPS 地址，再在仓库 Settings → Secrets and variables → Actions → Variables 添加 `API_BASE_URL`，例如 `https://后端域名/api`（不含密钥）。后端 `ALLOWED_ORIGINS=https://chenxiaozao.github.io`，来源是域名不含 /GG/ 路径。

仓库 Settings → Pages 选择 GitHub Actions，手动运行 Publish frontend to Pages。流程在没有后端地址时会拒绝发布，避免制造无法工作的页面。

预计前端地址为 https://chenxiaozao.github.io/GG/，具体以 GitHub 发布结果为准。采用 hash 路由，例如 /GG/#/products，刷新不会要求服务器解析 Vue 路由。前端静态图片带 /GG/ 前缀，用户头像从后端加载。

独立部署 Java 时启用 `SPRING_PROFILES_ACTIVE=prod`，必填 DB_URL、DB_USERNAME、DB_PASSWORD。首次启动必填 BOOTSTRAP_ADMIN_PASSWORD。AVATAR_DIR 指向持久化挂载目录。MySQL 配置默认使用 MySQL 驱动；云端数据库连接的 TLS 参数按供应商要求设置，不能照搬容器内部连接参数。

## 安全变更及限制

- 关闭 H2 控制台；prod 不创建演示用户，发现常用演示密码拒绝启动；从本地数据库迁移前先修改密码，推荐使用全新线上数据库。
- API 使用统一拦截器：公开商品、规则推荐等白名单以外需登录；管理路径需管理员；原有订单/地址/购物车所有权检查保留。
- token 仅在登录响应单独返回，不随用户信息/用户列表泄露。登录返回 {token,user}，旧前端脚本需按该结构读取。
- AI 对话要求登录；每账号每分钟最多 3 次、每 24 小时最多 20 次、全站每 24 小时最多 100 次尝试（失败也占额度）。可通过 AI_USER_DAILY_LIMIT、AI_GLOBAL_DAILY_LIMIT 调整，0 可禁用；单次问题限制 2000 字，历史消息限制 4000 字，模型输出已限制 token。
- 限流是单进程内存计数，重启重置；多实例或需要严格计费预算时必须改为共享持久计数，并设置服务商侧预算。它不是防 DDoS 或绝对费用保证。登录注册每来源 IP 每分钟 20 次；代理部署时按代理 IP 计数，不信任任意 X-Forwarded-For，可能合并多个用户的限额。
- 仅允许配置的前端来源跨域；来源限制不能替代身份鉴权。
- HTTPS、云资源、备份计划、在线可用性与实际部署验收仍需在选定服务器后完成。Docker 配置尚需目标环境验证，不能以本地 Maven 测试代替云端运行验证。
- 历史曾在聊天截图中展示的 DeepSeek Key 应在服务商后台撤销后重新生成，不能继续用于公开部署。不要把新 Key 发到聊天或填写 VITE_*。

## 发布清单

仅上传 server、web、README、.gitignore 和 .github。禁止上传 data、uploads、日志、.env、私钥、论文和申报材料。商品素材保留演示用途，不代表拥有再授权权利。README 内指向本地 docs 和一键脚本的旧说明不属于精简仓库内容。
