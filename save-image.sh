#!/bin/sh
target="geodata-server/target/geodata-server_latest.tar.gz"
docker save ethlo/geodata-server:1.0.0-SNAPSHOT | gzip > "$target"
echo "Saved to $target"
