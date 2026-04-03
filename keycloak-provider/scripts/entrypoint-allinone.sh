#!/bin/bash
set -euo pipefail

POSTGRES_USER="${POSTGRES_USER:-postgres}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"
POSTGRES_DB="${POSTGRES_DB:-miniWeb}"

export PGPASSWORD="$POSTGRES_PASSWORD"

# Khởi động PostgreSQL (logic chuẩn của image postgres)
/usr/local/bin/docker-entrypoint.sh postgres &
_PG_PID=$!

for _ in $(seq 1 90); do
  if pg_isready -h 127.0.0.1 -p 5432 -U "$POSTGRES_USER" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! pg_isready -h 127.0.0.1 -p 5432 -U "$POSTGRES_USER" >/dev/null 2>&1; then
  echo "ERROR: PostgreSQL did not become ready in time" >&2
  exit 1
fi

# Đảm bảo database tồn tại (POSTGRES_DB thường đã được tạo sẵn)
if ! psql -h 127.0.0.1 -U "$POSTGRES_USER" -d postgres -tc "SELECT 1 FROM pg_database WHERE datname = '$POSTGRES_DB'" | grep -q 1; then
  psql -h 127.0.0.1 -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE \"$POSTGRES_DB\""
fi

export USER_STORAGE_JDBC_URL="jdbc:postgresql://127.0.0.1:5432/${POSTGRES_DB}"
export USER_STORAGE_JDBC_USER="$POSTGRES_USER"
export USER_STORAGE_JDBC_PASSWORD="$POSTGRES_PASSWORD"

# Sau khi Keycloak sẵn sàng, tự thêm User Federation (realm master) nếu chưa có
(
  set +e
  for _ in $(seq 1 180); do
    if curl -fsS "http://127.0.0.1:8080/health/ready" >/dev/null 2>&1 \
      || curl -fsS "http://127.0.0.1:8080/q/health/ready" >/dev/null 2>&1; then
      /configure-federation.sh
      exit 0
    fi
    sleep 2
  done
  echo "WARN: Keycloak chua ready du lau — bo qua tu dong them User Federation" >&2
) &

exec /opt/keycloak/bin/kc.sh start-dev
