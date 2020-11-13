#!/bin/sh
set -e
[ -z "$GEODATA_GEOLITE2_LICENSE_KEY" ] && { echo "Missing environment variable GEODATA_GEOLITE2_LICENSE_KEY. You need to sign up with MaxMind (free) to get this license key"; exit 1; }
[ -z "$GEODATA_MAXDATAAGE" ] && { echo "Missing environment variable GEODATA_MAXDATAAGE. Set GEODATA_MAXDATAAGE=P7D for example"; exit 1; }

docker run --rm -m1G --name geodata-importer -v ~/geodata:/geodata-server/data \
--env GEODATA_GEOLITE2_LICENSE_KEY=$GEODATA_GEOLITE2_LICENSE_KEY \
--env GEODATA_MAXDATAAGE=$GEODATA_MAXDATAAGE \
ethlocom/geodata-importer:latest
