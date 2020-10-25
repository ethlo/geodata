#!/bin/sh
docker run --rm -m1G --name geodata-importer -v /tmp/geodata:/tmp/geodata \
--env GEODATA_GEOLITE2_LICENSE_KEY=LkYm1Qq27hK5vema \
--env GEODATA_MAX_DATA_AGE=P14D \
ethlo/geodata-importer:1.0.0-SNAPSHOT
