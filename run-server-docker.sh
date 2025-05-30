#!/bin/sh
docker run -d --rm -m1500M -p 6566:6565 --name geodata-server -v ~/geodata:/geodata-server/data docker.io/ethlocom/geodata-server:latest && docker logs geodata-server
