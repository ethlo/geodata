# Test fixture

A small dataset that `GeodataApplicationTests` imports with the real importer.

- `allCountries.txt`: a few real GeoNames rows (continents, NO/SE/GB/US, Norwegian counties, US states, the cities in the IP test database) plus synthetic rows with ids from 9100000 up. Rows with real ids are simplified too (rounded or made-up coordinates, timezones and populations), so don't use them as reference data. GeoNames data is CC BY 4.0, https://www.geonames.org/.
- `countryInfo.txt`, `shapes.tsv` (Norway and Sweden from `shapes_all_low`): GeoNames, CC BY 4.0.
- `GeoIP2-City-Test.tar.gz`: `GeoIP2-City-Test.mmdb` from https://github.com/maxmind/MaxMind-DB (test-data), packed as `GeoLite2-City.mmdb` the way the importer expects. Dual-licensed Apache-2.0 / MIT by MaxMind, Inc.
