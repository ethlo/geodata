#!/bin/sh
docker run -d --rm -m1500M -p 6566:6565 --name geodata-server -v ~/geodata:/tmp/geodata docker.io/ethlocom/geodata-server:latest && docker logs geodata-server
