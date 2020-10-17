-- CREATE ALIAS IF NOT EXISTS H2GIS_SPATIAL FOR "org.h2gis.functions.factory.H2GISFunctions.load";
-- CALL H2GIS_SPATIAL();

-- CREATE ALIAS IF NOT EXISTS FT_INIT FOR "org.h2.fulltext.FullText.init";
-- CALL FT_INIT();

create table metadata
(
    alias         varchar(32)  not null primary key,
    entry_count   int unsigned not null,
    last_modified datetime     not null
);

create table geocountry
(
    iso                  char(2) not null,
    iso3                 char(3),
    iso_numeric          int,
    fips                 varchar(255),
    country              char(255),
    capital              char(255),
    area                 double,
    population           int     not null,
    continent            char(2),
    tld                  varchar(255),
    currency_code        char(255),
    currency_name        char(255),
    phone                char(255),
    postal_code_format   varchar(255),
    postal_code_regex    varchar(255),
    languages            varchar(255),
    geoname_id           bigint  not null,
    neighbours           varchar(255),
    equivalent_fips_code varchar(255)
);

create table geohierarchy
(
    id        int unsigned not null,
    parent_id int unsigned not null,
    primary key (id)
);

create table timezone
(
    id    smallint unsigned not null auto_increment primary key,
    value varchar(200)      not null
);

create table feature_codes
(
    id            smallint unsigned not null auto_increment primary key,
    feature_class char(1)           not null,
    feature_code  varchar(80)       null,
    description   varchar(255)      null
);


create table geonames
(
    id               int unsigned not null primary key,
    name             varchar(200),
    feature_code_id  smallint unsigned,
    country_code     varchar(2),
    population       bigint,
    elevation_meters smallint,
    timezone_id      smallint unsigned,
    last_modified    date,
    admin_code1      varchar(20),
    admin_code2      varchar(80),
    admin_code3      varchar(20),
    admin_code4      varchar(20),
    coord            geometry(point)        not null
);

CREATE INDEX idx_filter on geonames (country_code, feature_code_id);
-- ALTER TABLE geonames ADD UNIQUE INDEX uniq_properties (country_code, feature_code_id, admin_code1, admin_code2, admin_code3, admin_code4);
CREATE SPATIAL INDEX geonames_coord ON geonames (coord);

-- MySQL
-- CREATE FULLTEXT INDEX ft_name_geonames on geonames (name);

-- H2
-- CALL FT_CREATE_INDEX(

create table geoip
(
    geoname_id         bigint not null,
    geoname_country_id bigint,
    first              bigint not null,
    last               bigint not null,
    precision_meters   int
);

-- InnoDB only
-- ALTER TABLE geoip ADD CONSTRAINT fk_geoname_id FOREIGN KEY (geoname_id) REFERENCES `geonames` (`id`);
-- ALTER TABLE geoip ADD CONSTRAINT fk_geoname_country_id FOREIGN KEY (geoname_country_id) REFERENCES `geonames` (`id`);

CREATE INDEX idx_range ON geoip (first, last);

CREATE TABLE geoboundaries
(
    id          bigint   NOT NULL PRIMARY KEY,
    raw_polygon geometry NOT NULL,
    coord       geometry NOT NULL,
    area        double   NOT NULL
);

CREATE SPATIAL INDEX polygon_idx ON geoboundaries (raw_polygon);
CREATE SPATIAL INDEX point_idx ON geoboundaries (coord);