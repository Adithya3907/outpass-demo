#!/bin/bash
# Starts a throw-away PostgreSQL and the Outpass demo in ONE container (used for the free public demo).
# Nothing persists: every restart builds a fresh database with fictional data.
set -e
PGBIN=$(ls -d /usr/lib/postgresql/*/bin | head -1)
PGDATA=/tmp/pgdata
rm -rf "$PGDATA"; mkdir -p "$PGDATA"
"$PGBIN/initdb" -D "$PGDATA" -U postgres --auth=trust >/dev/null
"$PGBIN/pg_ctl" -D "$PGDATA" -w -l /tmp/postgres.log \
  -o "-c listen_addresses=127.0.0.1 -c unix_socket_directories=/tmp -c fsync=off -c shared_buffers=64MB -c max_connections=40" start
"$PGBIN/psql" -h 127.0.0.1 -U postgres -q -c "CREATE USER outpass WITH PASSWORD 'demo';"
"$PGBIN/psql" -h 127.0.0.1 -U postgres -q -c "CREATE DATABASE outpass OWNER outpass;"

# nobody can use the break-glass admin password form on the public demo
export ADMIN_PASSWORD="$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)A1!"
exec java -Xmx700m -jar /app/app.jar
