#!/bin/sh
set -e
[ -z "$GEODATA_GEOLITE2_LICENSE_KEY" ] && { echo "Missing environment variable GEODATA_GEOLITE2_LICENSE_KEY. You need to sign up with MaxMind (free) to get this license key"; exit 1; }
[ -z "$GEODATA_MAXDATAAGE" ] && { echo "Missing environment variable GEODATA_MAXDATAAGE. Set GEODATA_MAXDATAAGE=P7D for example"; exit 1; }

docker run --rm -m2G --name geodata-server-import \
-v ~/geodata/data:/geodata-server/data \
-v ~/geodata/input:/geodata-server/input \
-v /tmp/geodata:/tmp/geodata \
--env GEODATA_IMPORT=1 \
--env GEODATA_GEOLITE2_LICENSE_KEY=$GEODATA_GEOLITE2_LICENSE_KEY \
--env GEODATA_MAXDATAAGE=$GEODATA_MAXDATAAGE \
ghcr.io/ethlo/geodata-server:latest
