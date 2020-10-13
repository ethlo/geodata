#/bin/sh
docker run --name geodata-clickhouse -p8123:8123 --ulimit nofile=262144:262144 yandex/clickhouse-server

