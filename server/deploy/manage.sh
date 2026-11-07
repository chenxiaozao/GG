#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "$(dirname "$0")"

compose() { docker compose --project-name robotmall-cloud --env-file .env -f compose.yml "$@"; }
require_docker() {
  command -v docker >/dev/null || { echo '请先按 README 安装 Docker Engine 与 Compose v2。' >&2; exit 1; }
  docker compose version >/dev/null
}
check() {
  [ -f .env ] || { echo '缺少 .env，请先 bash manage.sh init。' >&2; exit 1; }
  [ -s server.jar ] || { echo '缺少 server.jar，请使用 package.mjs 生成的完整部署包。' >&2; exit 1; }
  require_docker
  compose config --quiet  # Never print the interpolated configuration: it contains secrets.
}
case "${1:-help}" in
  init)
    [ ! -e .env ] || { echo '.env 已存在，拒绝覆盖。修改配置请在服务器本地编辑。' >&2; exit 1; }
    command -v openssl >/dev/null || { echo '需要 openssl 生成随机密码。' >&2; exit 1; }
    read -r -p '后端域名（例如 api.example.com，不含协议或路径）：' api_domain
    [[ ${#api_domain} -le 253 && "$api_domain" =~ ^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$ ]] || { echo '请输入有效的 DNS 域名。' >&2; exit 1; }
    read -r -p '证书通知邮箱：' acme_email
    [[ "$acme_email" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] || { echo '邮箱格式不正确。' >&2; exit 1; }
    db_password=$(openssl rand -hex 24)
    root_password=$(openssl rand -hex 24)
    admin_password=$(openssl rand -hex 16)
    # noclobber also prevents accidentally replacing a concurrently-created configuration.
    (set -o noclobber
      printf '%s\n' "API_DOMAIN=$api_domain" "ACME_EMAIL=$acme_email" \
        'ALLOWED_ORIGINS=https://chenxiaozao.github.io' \
        "DB_PASSWORD=$db_password" "MYSQL_ROOT_PASSWORD=$root_password" \
        "BOOTSTRAP_ADMIN_PASSWORD=$admin_password" 'DEEPSEEK_API_KEY=' \
        'DEEPSEEK_MODEL=deepseek-chat' 'AI_USER_DAILY_LIMIT=20' 'AI_GLOBAL_DAILY_LIMIT=100' > .env
    )
    chmod 600 .env
    printf '已生成私有 .env。管理员账号：admin；初始密码：%s\n请妥善保存并首次登录后改密，不要截图公开。\n' "$admin_password"
    ;;
  check) check; echo '配置语法已检查；尚未验证云端网络、证书或数据库运行。' ;;
  up) check; compose up -d --build --wait --wait-timeout 300 ;;
  start) check; compose up -d --wait --wait-timeout 300 ;;
  stop) check; compose stop; echo '容器已停止，数据卷保留。云主机通常仍收费，请以平台计费规则为准。' ;;
  status) check; compose ps ;;
  logs) check; compose logs --tail=100 backend gateway ;;
  backup) check; bash backup.sh ;;
  *) printf '%s\n' '用法：bash manage.sh init|check|up|start|stop|status|logs|backup' '更新：先 backup，按 README 更新 server.jar，再 up。没有删除数据卷的快捷命令。' ;;
esac
