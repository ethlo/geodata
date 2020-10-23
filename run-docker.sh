#!/bin/sh
docker run --rm -m1500M -p 6566:6565 --name geodata -v /tmp/geodata:/tmp/geodata ethlo/geodata-server:1.0.0-SNAPSHOT
