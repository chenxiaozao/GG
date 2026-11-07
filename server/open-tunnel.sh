#!/usr/bin/env bash
set -euo pipefail
if ! command -v cloudflared >/dev/null 2>&1; then
  printf '%s\n' '未安装 cloudflared。请先按 Cloudflare 官方文档安装；此脚本不会替你下载或注册外部服务。' 'https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/'
  exit 1
fi
printf '%s\n' '先用 run-on-demand.sh 启动后端；不要公开带默认演示密码的开发模式。' '开启后，知道临时网址的人可以访问后端；不要暴露数据库端口。' '网址通常每次变化，需更新 Pages API_BASE_URL 并重新发布。Ctrl+C关闭。'
read -r -p '确认后端使用独立数据与强密码，并同意临时公开？输入 OPEN：' choice
if [ "$choice" != OPEN ]; then exit 0; fi
exec cloudflared tunnel --url http://127.0.0.1:8080
