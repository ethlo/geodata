# Geodata
![Docker Image Version (latest semver)](https://img.shields.io/docker/v/ethlocom/geodata-server?sort=semver)
![Docker Image Size (latest by date)](https://img.shields.io/docker/image-size/ethlocom/geodata-server)
![Docker Pulls](https://img.shields.io/docker/pulls/ethlocom/geodata-server)

A simple, fast and free geodata server you can run anywhere. It is made for quick lookups:
* Find a location by name, IP address, phone number or coordinates.
* Check whether a location is inside another one, at any depth.
* Browse the hierarchy, from continents down through countries and administrative areas.

It is not a GIS tool for map analysis.

![Splash info page](docs/img/splash.jpg?raw=true)

It needs no database. The importer turns the source data into a few hundred MB of files, and the server memory-maps them on startup. Lookups run in memory on an Undertow handler, so expect thousands to hundreds of thousands of requests per second, depending on hardware.

The API is described with OpenAPI 3. The server has the docs at `/swagger-ui`, and the spec at `/spec.yaml`.
![OpenAPI docs](docs/img/openapi.jpg?raw=true)

## Import the data
The IP lookup uses MaxMind GeoLite2, which needs a free MaxMind account and license key. If you don't need IP lookups, turn off the `ip` feature and skip the key.

This is the script I use to build a data set:
```shell
docker run --memory=2G --rm --name geodata-server-import \
  -v ~/geodata/data:/geodata-server/data \
  -v ~/geodata/input:/geodata-server/input \
  -v /tmp/geodata:/tmp/geodata \
  -e GEODATA_IMPORT=1 \
  -e GEODATA_MAXDATAAGE=P14D \
  -e GEODATA_GEOLITE2_LICENSE_KEY=<license key> \
  ethlocom/geodata-server
```

Data younger than `GEODATA_MAXDATAAGE` is kept, so running it again only fetches what is outdated.

### Boundaries
Boundaries are not downloaded, you put them in `~/geodata/input/boundaries`:
* `.tsv` files with GeoNames shapes, with the columns `geoNameId` and `geoJSON`. Unpack `shapes_all_low.zip` from https://download.geonames.org/export/dump/ and rename the file to `shapes_all_low.tsv`.
* `<geonameId>.geojson` or `<geonameId>.kml` for a single location. These override the shape from the `.tsv` files.

The importer picks them up when a file in the folder is newer than the last import.

## Start the server
This loads the data from the import and listens on host port 6566:

```shell
docker run -d --rm -m1500M -p 6566:6565 --name geodata-server \
  -v ~/geodata/data:/geodata-server/data \
  ethlocom/geodata-server:latest && docker logs -f geodata-server
```

There is also a `docker-compose.yml` that does the same.

With about 5 million locations, the heap stays at around 300 MB. The rest of the memory is the memory-mapped data files, which the OS pages in as needed.

## Optional features
Locations, countries, continents, search by name and the hierarchy are always on. The rest are on by default, and each can be turned off with an environment variable:

| Feature       | Environment variable                   | Data                        | Endpoints                                                                     |
|---------------|----------------------------------------|-----------------------------|-------------------------------------------------------------------------------|
| `ip`          | `GEODATA_FEATURES_IP_ENABLED`          | MaxMind GeoLite2            | `/v1/locations/ip/{ip}`                                                       |
| `boundaries`  | `GEODATA_FEATURES_BOUNDARIES_ENABLED`  | Files in `input/boundaries` | `/v1/locations/{id}/boundaries` and its preview and simple variants, `/v1/locations/contains` |
| `coordinates` | `GEODATA_FEATURES_COORDINATES_ENABLED` | GeoNames locations          | `/v1/locations/coordinates`, `/v1/locations/proximity`, `/v1/locations/contains` |
| `phone`       | `GEODATA_FEATURES_PHONE_ENABLED`       | GeoNames countries          | `/v1/locations/phone/{phone}`                                                 |

Set it the same way for the importer and the server:
* The importer skips the data for a feature that is off, and removes data it imported for it before.
* The server turns a feature on only when it is enabled and its data was imported. It logs a warning when the data is missing.
* Endpoints of a feature that is off return `501 Not Implemented`. `/sysadmin/features` lists what is on.

Turning off `coordinates` also saves memory and startup time, since the server then skips the proximity index.

## Build
You need Java 25.

```shell
./build.sh
```

This builds the modules and the `ethlocom/geodata-server` image. The tests import a small data set that is checked in, so they need no download.

## Data sources
- [GeoNames](https://www.geonames.org/), available under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- This product includes GeoLite2 data created by MaxMind, available from https://www.maxmind.com.
