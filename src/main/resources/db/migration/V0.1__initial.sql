-- CREATE ALIAS IF NOT EXISTS H2GIS_FUNCTIONS FOR "org.h2gis.functions.factory.H2GISFunctions.load";
-- CALL H2GIS_FUNCTIONS();

create table geonames (
	id bigint not null primary key,
	PARENT_ID bigint,
	name varchar(200),
	feature_class char(1),
	feature_code varchar(10),
	country_code varchar(2),
	population bigint,
	elevation_meters int,
	timezone varchar(40),
	last_modified date,
	lat double not null,
	lng double not null,
	coord point not null
) engine=myisam;
CREATE SPATIAL INDEX geonames_coord ON geonames(coord);

create table geoip (
	geoname_id bigint not null,
	geoname_country_id bigint,
	first bigint not null,
	last bigint not null,
	precision_meters int
);
-- InnoDB only
-- ALTER TABLE geoip ADD CONSTRAINT fk_geoname_id FOREIGN KEY (geoname_id)  REFERENCES `geonames` (`id` );
-- ALTER TABLE geoip ADD CONSTRAINT fk_geoname_country_id FOREIGN KEY (geoname_country_id)  REFERENCES `geonames` (`id`);

CREATE INDEX idx_range ON geoip(first, last);

CREATE TABLE geoboundaries (
  id bigint NOT NULL PRIMARY KEY,
  raw_polygon geometry NOT NULL,
  coord geometry NOT NULL,
  area double not null
) engine=myisam;  
CREATE SPATIAL INDEX polygon_idx ON geoboundaries(raw_polygon);
CREATE SPATIAL INDEX point_idx ON geoboundaries(coord);