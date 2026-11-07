#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "$(dirname "$0")"
if [ ! -f data/on-demand/robotmall.mv.db ] && [ -z "${BOOTSTRAP_ADMIN_PASSWORD:-}" ]; then
  read -r -s -p '设置首次管理员密码（至少12位，不会保存到脚本）：' BOOTSTRAP_ADMIN_PASSWORD
  printf '\n'
  export BOOTSTRAP_ADMIN_PASSWORD
fi
export SPRING_PROFILES_ACTIVE=on-demand
mvn -q -DskipTests package
exec java -Xms128m -Xmx512m -jar target/robotmall-server-1.0.0.jar
