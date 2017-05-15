CREATE ALIAS IF NOT EXISTS H2GIS_FUNCTIONS FOR "org.h2gis.functions.factory.H2GISFunctions.load";
CALL H2GIS_FUNCTIONS();

create table geonames (
	id int not null primary key,
	name varchar(200),
	feature_class char(1),
	feature_code varchar(10),
	country_code varchar(2),
	population bigint,
	elevation_meters int,
	timezone varchar(40),
	last_modified int,
	lat double,
	lng double,
	coord point
);

create table geo_ip (
	geoname_id int not null,
	geoname_country_id int,
	first int not null,
	last int not null,
	lat double,
	lng double,
	precision_meters int
);