package com.ethlo.geodata.importer.jdbc;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.flywaydb.core.internal.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.ethlo.geodata.importer.CountryImporter;
import com.ethlo.geodata.util.ResourceUtil;

@Component
public class JdbcCountryImporter implements PersistentImporter
{
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${geodata.geonames.source.country}")
    private String url;

    @Override
    public void importData()
    {
        try
        {
            final Map.Entry<Date, File> countryFile = ResourceUtil.fetchResource("geocountry", url);

            final CountryImporter importer = new CountryImporter(countryFile.getValue());
            importer.processFile(entry ->
            {
                jdbcTemplate.update(makeSql("geocountry", entry), entry);
            });
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }

    private String makeSql(String tablename, Map<String, String> entry)
    {
        final List<String> placeholders = entry.keySet().stream().map(e -> ":" + e).collect(Collectors.toList());
        return "INSERT INTO `" + tablename + "`("
                + StringUtils.collectionToCommaDelimitedString(entry.keySet()) + ") "
                + "VALUES(" + StringUtils.collectionToCommaDelimitedString(placeholders) + ")";
    }

    @Override
    public void purge()
    {
        jdbcTemplate.update("DELETE FROM geocountry", Collections.emptyMap());
    }

    @Override
    public Date lastRemoteModified()
    {
        try
        {
            return ResourceUtil.getLastModified(url);
        }
        catch (IOException exc)
        {
            throw new UncheckedIOException(exc);
        }
    }
}
