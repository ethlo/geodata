# Geodata
![Docker Image Version (latest semver)](https://img.shields.io/docker/v/ethlocom/geodata-server?sort=semver)
![Docker Image Size (latest by date)](https://img.shields.io/docker/image-size/ethlocom/geodata-server)
![Docker Pulls](https://img.shields.io/docker/pulls/ethlocom/geodata-server)
![Splash info page](docs/img/splash.jpg?raw=true)

### Features
Simple, fast and free geo-data server for flexible deployment. This service is intended for doing quick lookups, like:
* Searching for a location by name, ip, telephone number, lat/long coordinates
* Query if a location is a child, grand-child, etc of another location
* Browse location hierarchy (starting from continents and moving down administrative locations)

NOTE: This is not an advanced GIS tool for map manipulation and analysis.

### Performance
Low memory overhead and super-fast response times utilizing appropriate fit-for-purpose data-structures, and a highly optimized HTTP handler built on UnderTow. Depending on hardware you can expect thousands to hundreds-of-thousands of requests per second.

### Ease-of-use
For quick and easy distribution of data the system utilizes no database. Typical data imports are a few hundred mega bytes, 
and require no further processing or data-loading before running on a server.

### Fully documented RESTful API
The API is fully documented using Open API 3.x specification and allows for easy consumption from numerous platforms.
![OpenAPI docs](docs/img/openapi.jpg?raw=true)

## Usage guide
### Import and process data
Prerequisite: You need to register for an account at MaxMind (free) for the GeoLite2 data, unless the `ip` feature is disabled (see [Optional features](#optional-features)).

Simple, sample script I use for creating data-set for the server:
```shell script
docker run --memory=2G --rm --name geodata-server-import \
-v ~/geodata/data:/geodata-server/data \
-v ~/geodata/input:/geodata-server/input \
-v /tmp/geodata:/tmp/geodata \
-e "GEODATA_IMPORT=1" \
-e "GEODATA_MAXDATAAGE=P14D" \
-e "GEODATA_GEOLITE2_LICENSE_KEY=<<LICENSE>>" ethlocom/geodata-server
```

### Start the server

Start the server and load the data from the import step. Listen on host port 6566. 

```shell script
docker run -d --rm -m1G -p 6566:6565 --name geodata-server -v ~/geodata/data:/geodata-server/data \
-e JAVA_OPTS="-XX:MaxDirectMemorySize=1G" ethlocom/geodata-server:latest \ 
&& docker logs -f geodata-server
```

### Optional features

Locations, countries, continents, search by name and the hierarchy are always available. The rest can be turned off, all are on by default:

| Feature       | Property / environment variable                                            | Data source             | Endpoints                                                                                          |
|---------------|----------------------------------------------------------------------------|-------------------------|----------------------------------------------------------------------------------------------------|
| `ip`          | `geodata.features.ip.enabled` / `GEODATA_FEATURES_IP_ENABLED`                   | MaxMind GeoLite2        | `/v1/locations/ip/{ip}`                                                                            |
| `boundaries`  | `geodata.features.boundaries.enabled` / `GEODATA_FEATURES_BOUNDARIES_ENABLED`   | Files in `input/boundaries` | `/v1/locations/{id}/boundaries`, `previewboundaries`, `simpleboundaries`, `/v1/locations/contains` |
| `coordinates` | `geodata.features.coordinates.enabled` / `GEODATA_FEATURES_COORDINATES_ENABLED` | Geonames (locations)    | `/v1/locations/coordinates`, `/v1/locations/proximity`, `/v1/locations/contains`                   |
| `phone`       | `geodata.features.phone.enabled` / `GEODATA_FEATURES_PHONE_ENABLED`             | Geonames (countries)    | `/v1/locations/phone/{phone}`                                                                      |

* The importer skips the data of a disabled feature and removes data imported for it earlier, so with `ip` off no MaxMind license key is needed. When IP data is (re)imported, the locations are re-imported too, since they include the cities the IP data refers to.
* The server enables a feature only when it is enabled *and* its data was imported (according to `metadata.json`), and logs a warning for an enabled feature without data. With `coordinates` off, no proximity tree is built, which saves memory and startup time.
* `/v1/locations/coordinates` returns the containing boundary when `boundaries` is on, and the nearest location otherwise.
* Endpoints of a disabled feature return `501 Not Implemented`. `/sysadmin/features` lists the features that are available.

## This service utilize data from:

- Geonames.org - Free data is available under [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/) license
- This product includes GeoLite2 data created by MaxMind, available from https://www.maxmind.com.
