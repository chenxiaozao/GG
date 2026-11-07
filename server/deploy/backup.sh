#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "$(dirname "$0")"
[ -f .env ] || { echo '缺少私有 .env。' >&2; exit 1; }
compose() { docker compose --project-name robotmall-cloud --env-file .env -f compose.yml "$@"; }
mkdir -p backups
destination=$(mktemp -d "$(pwd)/backups/backup-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX")
echo '正在生成备份；执行期间请勿进行上传、下单或管理操作。'
compose exec -T database sh -ec 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysqldump --user="$MYSQL_USER" --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' | gzip > "$destination/database.sql.gz"
compose exec -T backend tar -C /app/data/avatars -czf - . > "$destination/avatars.tar.gz"
(cd "$destination" && sha256sum database.sql.gz avatars.tar.gz > SHA256SUMS)
echo "备份完成：$destination"
echo '含个人数据，请加密后复制至另一台设备；同机数据卷和同机备份不能抵御整台服务器丢失。'
echo '这不是跨数据库与文件系统的原子快照；请在无人操作时执行。私有 .env 请另行安全保管。'
