# GitHub Pages 前端 + 云端后端部署手册

本目录是**后端专用**方案，不会在云端再部署一份 Vue 网页。现在仍处于部署准备阶段，没有购买服务器，没有公网地址，没有完成云端上线验收。

## 1. 最终访问方式

```text
电脑/手机浏览器
  ├─ https://chenxiaozao.github.io/GG/   GitHub Pages：页面、商品静态图片
  └─ https://你的后端域名/api            云端 Caddy HTTPS → Java API → MySQL
            └─ /uploads/avatars/        云端持久保存的用户上传头像
```

账号、购物车、收藏、地址、选购计划、偏好授权、模拟订单和管理记录保存到同一个云端数据库。用户不需要下载源码、安装 Java 或连接你的电脑。购物/支付仍是模拟，不接入真实资金。

## 2. 先不急着购买

- 针对此项目的低并发演示，我建议从 **2 vCPU、2 GiB 内存**起步，4 GiB 更宽裕。这是工程容量建议，不是压测承诺；1 GiB 同时运行 Java 与 MySQL 太紧张，不建议仅因月费便宜就选。
- 系统可选 Ubuntu 24.04 LTS，安装 Docker Engine + Compose v2。不要选 Windows，也不需要另买托管数据库、Redis、对象存储或面板。
- **还需要后端域名及有效 HTTPS**。GitHub Pages 是 HTTPS，不能把 `http://公网IP:8080` 当成可用的正式 API 地址。本方案默认使用 DNS 域名及 Caddy 自动证书，不提供“只填一个 IP 就全部完成”的承诺。
- 中国内地服务器用于 Web 服务的备案要求、资源购买资格以阿里云官方说明为准；非中国内地地域存在跨境延迟，GitHub Pages 在不同网络的可用性也需实测。先确定地域、域名和预算，再下单，不承诺过去截图中的 28 元套餐仍存在或适合本方案。
- 不必为了这个演示单独采购一套企业级云产品。若选择其他支持 Docker、持久磁盘和自定义 HTTPS 域名的平台，部署结构也可复用；免费容器平台的休眠、磁盘和数据库限制必须另核实，不能假定“免费永久保留账号”。

官方入口：[阿里云创建轻量服务器](https://help.aliyun.com/zh/simple-application-server/user-guide/create-a-server)、[使用限制及备案提示](https://www.alibabacloud.com/help/en/simple-application-server/product-overview/usage-notes)、[Docker Ubuntu 安装步骤](https://docs.docker.com/engine/install/ubuntu/)、[GitHub Pages 范围](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages)。

## 3. 文件与数据各放哪里

| 内容 | 位置 | 是否进 GitHub/发布包 |
|---|---|---|
| Vue 前端、商品静态图 | GitHub `web/`，发布到 Pages | 可以 |
| Java 源码、公开配置模板 | GitHub `server/` | 可以，不含凭据 |
| Java 可运行 JAR | 本机生成的部署包 | 只上传云主机，不进仓库 |
| 云数据库 | `robotmall-cloud_database` 数据卷 | 不进入源码或初始包 |
| 用户头像 | `robotmall-cloud_avatars` 数据卷 | 不进入源码或初始包 |
| TLS 证书 | `robotmall-cloud_certificates` 数据卷 | 私有，不公开 |
| 数据库/管理员密码、DeepSeek Key | 云主机部署目录 `.env`，权限 600 | 绝对不上传 GitHub |
| 后端公开地址 | GitHub Actions Variable `API_BASE_URL` | 公开地址，不是密钥 |

Docker 卷不是备份。普通重建保留卷，删除卷、释放云盘、到期销毁或换一个 Compose 项目名都可能导致数据丢失或看起来像全新数据库。脚本固定项目名 `robotmall-cloud`，禁止随意更改。

## 4. 本机生成部署包（现在即可做）

在开发电脑、项目根目录执行：

```sh
node server/deploy/package.mjs
```

脚本先运行 Maven 测试并打包，再生成 `tmp/deployment/robotmall-backend-时间.tar.gz`。这是白名单打包，只包含 JAR、云端配置、管理脚本、公开模板和本手册。不会复制本机真实 `.env`、数据库、头像、日志、前端、论文或软著材料。

本机补充验证：在 `server/` 中运行 `node deployment-smoke.mjs --prod`，会使用 prod 配置并显式替换成临时 H2 数据库、临时头像目录和本机 18080 端口，不访问实际云数据库、不调用 AI。这个检查与 MySQL/Docker/公网证书验收分开。

包内 `SHA256SUMS` 用来检测上传损坏，不是来源签名。以后代码更新要重新生成部署包，不能只复制旧包。

## 5. 有服务器后进行的操作

以下命令在**阿里云服务器终端**执行，不是在你的 Mac 终端盲目照抄。不要现在就买资源只为执行这些命令。

1. 按 Docker 官方 Ubuntu 文档安装 Docker Engine、Buildx、Compose 插件，运行 `sudo docker compose version` 确认 Compose v2。本文操作假设使用有 Docker 权限的管理员终端；Docker 组权限等同高权限，请勿随意授权。
2. 云防火墙仅开放 TCP 80、443；SSH 22 尽量限定你自己的 IP。**不要开放 3306、8080**。Docker 暴露端口有自己的转发规则，不能只依赖主机 UFW。
3. 用域名管理控制台建立 A 记录，例如 `api.你的域名` → 服务器公网 IPv4。若没有正确配置 IPv6，不要加 AAAA 记录。域名和证书使用必须符合实际地域要求。
4. 将后端包上传到服务器自己的部署目录，例如 `/opt/robotmall/releases/`，解压。第一次部署保留一个固定运行目录 `/opt/robotmall/current/`；把包里的 `robotmall-backend` 内容放在其中。以后的更新不要覆盖 `.env`。
5. 在运行目录中校验并初始化：

```sh
sha256sum -c SHA256SUMS
bash manage.sh init
```

初始化会询问后端域名和证书通知邮箱，随机生成数据库、root 和管理员三个不同密码，不覆盖已有 `.env`。管理员账号是 `admin`，初始密码只在终端提示，请妥善保存，不要截图发聊天。首次登录后在账号安全中改密。

6. 若需要 AI，在**服务器本地**用编辑器打开 `.env`，填写新的 `DEEPSEEK_API_KEY`。曾在聊天截图中公开的旧 Key 必须在服务商处撤销；不要从旧配置自动复制。Key 留空时，注册、商城和规则推荐仍可用，AI 对话会明确显示未配置。
7. 检查配置和启动：

```sh
bash manage.sh check
bash manage.sh up
bash manage.sh status
```

启动顺序是 MySQL 健康 → Java 数据初始化与数据库检查完成 → HTTPS 网关。首次拉镜像需要联网，可能较慢。`up --wait` 等待容器健康，**不代表域名证书已签发成功**；继续下一步公开地址验收。

8. 在自己的浏览器打开 `https://你的后端域名/api/health`，应得到 `code=200`、`data.status=UP`，不能有证书警告。根地址 `/` 返回 404 是正常的：网页在 GitHub，不在这台后端上。

若失败：先看 `bash manage.sh logs`，检查域名解析、云端 80/443、防火墙、容器状态、内存和证书错误。不把 `.env` 或完整凭据发聊天。Caddy 证书签发需要可验证的域名及网络条件，见[自动 HTTPS 官方说明](https://caddyserver.com/docs/automatic-https)。

## 6. 连接 GitHub Pages（必须后端 HTTPS 可用后做）

1. 仓库 `chenxiaozao/GG` → Settings → Secrets and variables → Actions → **Variables** → New repository variable。
2. 名称 `API_BASE_URL`，值 `https://你的后端域名/api`。不是 IP 的 HTTP 地址，不是 GitHub 网页地址，也不是 DeepSeek 地址。
3. Settings → Pages → Source 选择 **GitHub Actions**。
4. Actions → `Publish frontend to Pages` → Run workflow。代码必须在你明确允许后上传；本次整理不自动 push。
5. 工作流会检查健康状态和 Pages 来源跨域预检，再构建发布。若变量留空，则仍发布只读目录版，不会偷偷启用账号功能。
6. 打开 `https://chenxiaozao.github.io/GG/`，用**新测试账号**验证注册、登录、收藏、购物车、模拟下单、头像上传和重新登录。所有设备使用云端同一账号库，不是各自本地独立账号。

`ALLOWED_ORIGINS` 应为 `https://chenxiaozao.github.io`，**不含 `/GG/` 路径**。若增加自己的前端域名或更换 GitHub 用户名，要同步修改此项和 GitHub 公开 API 地址，再重建后端/重新发布前端。不要简单设为 `*`。

Pages 地址和后端地址是两件事；后端换域名后需要重新构建前端。静态商品图继续从 Pages 读取，上传头像由浏览器从后端 HTTPS 地址读取。`server.jar` 不需要附带 `web/`。

## 7. 日常管理

```sh
bash manage.sh status     # 查看状态
bash manage.sh logs       # 最近 100 行，不主动打印配置
bash manage.sh stop       # 暂停服务，保留数据库/头像/证书
bash manage.sh start      # 再开启
bash manage.sh backup     # 在无人操作时备份数据库和头像
```

- 关闭自己的 Mac 不影响云端。停止云端后端时，前端仍在，但只读降级；不能登录、保存或使用 AI。
- **停止容器不等于停止云资源计费**，按实际云套餐核实，不能据此承诺按使用分钟付费。
- 修改 `.env` 后用 `bash manage.sh up` 重建应用配置，而不是只重启浏览器。
- 初始化之后改 `.env` 的数据库密码不会自动修改已存在 MySQL 卷里的账号密码；不要随意修改。管理员密码也不会通过再次修改启动变量自动重置。请使用应用中的改密/管理功能。
- Caddy 覆盖客户端转发头，Java 只在私网代理后解析真实来源地址，避免多人共享一个代理 IP 的登录限流。不要额外映射 Java 公网端口，也不要在前面增加不受控代理后假定该信任关系仍成立。
- AI 用户/全站日限额可在 `.env` 调低；设为 0 可停用。额度是应用级保护，不是绝对费用上限；供应商端还应管理余额/预算。本手册不运行真实 AI 测试。
- 日志有轮转限制；不要开启请求正文或 Token 调试日志。容器资源限制按低并发起步设置，需要监测内存/磁盘后再调，不保证任意并发量。

## 8. 更新、备份与恢复边界

更新前先执行备份，另行安全保存 `.env`。在开发电脑重新打包，将新包上传到独立 releases 子目录并校验。保留旧 JAR，**只用新 JAR 和审阅过的模板更新固定运行目录**，不要覆盖 `.env`，再运行 `bash manage.sh up`。保持同一 Compose 项目名，禁止 `docker compose down -v`。

备份脚本在 `backups/backup-时间-随机后缀/` 输出数据库 SQL 压缩包、头像归档及 SHA256。含账号、地址、会话等个人数据，文件权限受限，不得上传公开仓库；应加密复制到另一台设备。备份期间不要上传/下单，这不是数据库和文件系统的跨介质原子快照。

**恢复必须先在新主机或隔离的新数据库/新卷中演练。** 应先校验备份完整性，关闭新环境后端写入，导入 SQL，再把头像恢复到新环境头像卷，然后启动并验证账号、订单、头像及权限。SQL 导入可能覆盖同名表，不应直接试在已有正式数据上；需要实际恢复时再根据目标数据状态执行。当前没有云资源，恢复、MySQL 容器和证书均尚未做目标环境演练。

当前 JPA 使用 `ddl-auto=update`，适合此演示的初次部署，但不是可审计的数据库版本迁移系统。涉及删列/改类型等后续升级必须先备份并评审迁移；回滚旧 JAR 不保证能回滚数据库。

## 9. 上线验收表（不能提前勾成已完成）

- [ ] 云主机购买完成、域名已解析、地域合规要求已处理。
- [ ] 仅 80/443 公开；数据库和 Java 端口外部不可访问。
- [ ] HTTPS 无告警，`/api/health` 可用，重启后仍可用。
- [ ] Pages 发布成功，真实跨域注册/登录正常，页面不是只读快照模式。
- [ ] 新账号数据相互隔离，管理接口不能由普通账号访问。
- [ ] 头像、购物车、计划、订单重启后仍存在。
- [ ] 新的 AI Key 由用户自行设置；另行获准后才进行付费真实调用。
- [ ] 实际备份及隔离恢复演练成功。
- [ ] 手机和电脑在目标网络实际访问测试完成。

本地 Maven、打包、配置结构检查通过不等于这些公网事项已完成。云端身份、域名、付费和发布操作均需用户决定。
