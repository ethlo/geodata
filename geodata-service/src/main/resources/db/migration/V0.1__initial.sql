create table metadata
(
    alias         varchar(32) not null primary key,
    last_modified datetime    not null
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

create table geonames
(
    id               bigint not null primary key,
    parent_id        bigint,
    name             varchar(200),
    feature_class    char(1),
    feature_code     varchar(10),
    country_code     varchar(2),
    population       bigint,
    elevation_meters int,
    timezone         varchar(40),
    last_modified    date,
    admin_code1      varchar(20),
    admin_code2      varchar(80),
    admin_code3      varchar(20),
    admin_code4      varchar(20),
    lat              double not null,
    lng              double not null,
    coord            point  not null
) engine = InnoDB
  character set utf8;
ALTER TABLE geonames
    ADD INDEX idx_filter (country_code, feature_code);
CREATE SPATIAL INDEX geonames_coord ON geonames (coord);
CREATE FULLTEXT INDEX ft_name_geonames on geonames (name);

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
) engine = innodb;
CREATE SPATIAL INDEX polygon_idx ON geoboundaries (raw_polygon);
CREATE SPATIAL INDEX point_idx ON geoboundaries (coord);