package com.ethlo.geodata;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.yandex.clickhouse.ClickHouseDataSource;

@Configuration
public class ClickhouseDatasourceCfg
{
    @Bean
    public DataSource clickhouseDatasource(@Value("${clickhouse.datasource.url}") String url)
    {
        return new ClickHouseDataSource(url);
    }
}