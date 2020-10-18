package com.ethlo.geodata.dao.jdbc;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public abstract class JdbcBaseDao
{
    @Autowired
    protected NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    protected DataSource dataSource;

    protected long count(final String sql, final Map<String, Object> params)
    {
        final Long result = jdbcTemplate.queryForObject(sql, params, Long.class);
        return result != null ? result : 0;
    }
}
