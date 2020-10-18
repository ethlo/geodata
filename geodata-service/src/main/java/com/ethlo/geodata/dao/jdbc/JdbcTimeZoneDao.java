package com.ethlo.geodata.dao.jdbc;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.TimeZoneDao;

@Repository
public class JdbcTimeZoneDao extends JdbcBaseDao implements TimeZoneDao
{
    @Override
    public Map<Integer, String> findTimeZones()
    {
        final Map<Integer, String> timezones = new HashMap<>();
        jdbcTemplate.query("SELECT * FROM timezone", rs -> {
            while (rs.next())
            {
                final int id = rs.getInt("id");
                final String timezone = rs.getString("value");
                timezones.put(id, timezone);
            }
        });
        return timezones;
    }
}
