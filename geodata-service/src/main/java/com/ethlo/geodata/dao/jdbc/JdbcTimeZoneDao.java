package com.ethlo.geodata.dao.jdbc;

/*-
 * #%L
 * Geodata service
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

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
