package com.ethlo.geodata.importer;

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

import static org.apache.commons.lang3.StringUtils.stripToNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.springframework.util.StringUtils;

import com.ethlo.geodata.IoUtils;

public class CountryImporter implements PushDataImporter
{
    private final File csvFile;

    public CountryImporter(File csvFile)
    {
        this.csvFile = csvFile;
    }

    @Override
    public long processFile(Consumer<Map<String, String>> sink) throws IOException
    {
        /*
         * ISO
         * ISO3
         * ISO-Numeric
         * fips
         * Country
         * Capital
         * Area(in sq km)
         * Population
         * Continent
         * tld
         * CurrencyCode
         * CurrencyName
         * Phone
         * Postal Code Format
         * Postal Code Regex
         * Languages
         * geonameid
         * neighbours
         * EquivalentFipsCode
         */
        long count = 0;
        try (final BufferedReader reader = IoUtils.getBufferedReader(csvFile))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (!line.startsWith("#"))
                {
                    final String[] entry = StringUtils.delimitedListToStringArray(line, "\t");
                    final Map<String, String> paramMap = new TreeMap<>();
                    paramMap.put("iso", stripToNull(entry[0]));
                    paramMap.put("iso3", stripToNull(entry[1]));
                    paramMap.put("iso_numeric", stripToNull(entry[2]));
                    paramMap.put("fips", stripToNull(entry[3]));
                    paramMap.put("country", stripToNull(entry[4]));
                    paramMap.put("capital", stripToNull(entry[5]));
                    paramMap.put("area", stripToNull(entry[6]));
                    paramMap.put("population", stripToNull(entry[7]));
                    paramMap.put("continent", stripToNull(entry[8]));
                    paramMap.put("tld", stripToNull(entry[9]));
                    paramMap.put("currency_code", stripToNull(entry[10]));
                    paramMap.put("currency_name", stripToNull(entry[11]));
                    paramMap.put("phone", stripToNull(entry[12]));
                    paramMap.put("postal_code_format", stripToNull(entry[13]));
                    paramMap.put("postal_code_regex", stripToNull(entry[14]));
                    paramMap.put("languages", stripToNull(entry[15]));
                    paramMap.put("geoname_id", stripToNull(entry[16]));
                    paramMap.put("neighbours", stripToNull(entry[17]));
                    paramMap.put("equivalent_fips_code", stripToNull(entry[18]));

                    sink.accept(paramMap);
                }

                count++;
            }
        }
        return count;
    }
}
