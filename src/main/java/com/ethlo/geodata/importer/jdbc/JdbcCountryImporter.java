package com.ethlo.geodata.importer.jdbc;

/*-
 * #%L
 * geodata
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.File;
import java.io.IOException;
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
    public void importData() throws IOException
    {
        final Map.Entry<Date, File> countryFile = ResourceUtil.fetchResource("geocountry", url);
        
        final CountryImporter importer = new CountryImporter(countryFile.getValue());
        importer.processFile(entry->
        {
            jdbcTemplate.update(makeSql("geocountry", entry), entry);
        });
    }

    private String makeSql(String tablename, Map<String, String> entry)
    {
        final List<String> placeholders = entry.keySet().stream().map(e->":"+e).collect(Collectors.toList());
        return "INSERT INTO `" + tablename + "`(" 
            + StringUtils.collectionToCommaDelimitedString(entry.keySet()) + ") "
            + "VALUES(" + StringUtils.collectionToCommaDelimitedString(placeholders) + ")";
    }

    @Override
    public void purge() throws IOException
    {
        jdbcTemplate.update("DELETE FROM geocountry", Collections.emptyMap());
    }

    @Override
    public Date lastRemoteModified() throws IOException
    {
        return ResourceUtil.getLastModified(url);
    }
}
