# Geodata
![Docker Image Version (latest semver)](https://img.shields.io/docker/v/ethlocom/geodata-server?sort=semver)
![Docker Image Size (latest by date)](https://img.shields.io/docker/image-size/ethlocom/geodata-server)
![Docker Pulls](https://img.shields.io/docker/pulls/ethlocom/geodata-server)
![Splash info page](docs/img/splash.jpg?raw=true)

> :warning: Fast-moving active development!

### Features
Simple, fast and free geo-data server for flexible deployment. This service is intended for doing quick lookups, like:
* IP-to-location
* searching for a location by name
* query if a location is a child, grand-child, etc of another location
* Browse location hierarchy (starting from continents and moving down administrative locations)
* etc, etc, and not map/GIS tool

### Performance
Quick startup, low memory overhead and super-fast response times utilizing 
appropriate fit-for-purpose data-structures, and a highly optimized HTTP handler built on UnderTow allows hundreds of thousands of queries per second.

### Ease-of-use
For quick and easy distribution of data the system utilizes no database. Typical data imports are a few hundred mega bytes, 
and require no further processing or data-loading before running on a server.

### Fullt documented API
The API is fully documented using Open API 3.x specification and allows for easy consumption from numerous platforms.
![OpenAPI docs](docs/img/openapi.jpg?raw=true)

## Usage guide
### Import and process data
You need to register for an account at MaxMind (free) for the GeoLite2 data

```shell script
docker run --rm -m1G --name geodata-importer -v ~/geodata:/tmp/geodata \
--env GEODATA_GEOLITE2_LICENSE_KEY=<LICENSE_KEY> \
--env GEODATA_MAXDATAAGE=P7D \
ethlocom/geodata-importer:latest
```

### Start the server

Start the server and load the data from the import step. Listen on host port 6566. 

```shell script
docker run -d --rm -m1G -p 6566:6565 --name geodata-server -v ~/geodata:/tmp/geodata docker.io/ethlocom/geodata-server:latest \ 
&& docker logs geodata-server
```

## This service utilize data from:

- Geonames.org - Free data is available under [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/) license
- This product includes GeoLite2 data created by MaxMind, available from https://www.maxmind.com.