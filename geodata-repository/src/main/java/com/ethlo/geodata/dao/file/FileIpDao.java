package com.ethlo.geodata.dao.file;

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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.IpDao;
import com.google.common.primitives.Ints;
import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;
import com.maxmind.db.Reader;
import com.maxmind.db.ReaderIterator;

@Repository
public class FileIpDao implements IpDao
{
    public static final String IP_FILE = "geolite2.mmdb";

    private final Reader reader;

    public FileIpDao(@Value("${geodata.base-path}") final Path basePath)
    {
        final Path ipFile = basePath.resolve(IP_FILE);
        if (Files.exists(ipFile))
        {
            try
            {
                this.reader = new Reader(ipFile.toFile());
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        else
        {
            this.reader = null;
        }
    }

    @Override
    public Optional<Integer> findByIp(final InetAddress ip)
    {
        try
        {
            final LookupResult result = reader.get(ip, LookupResult.class);
            return Optional.ofNullable(result).map(LookupResult::getCity).map(LookupResult.City::getId);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void iterate(Consumer<Integer> callback) throws IOException
    {
        new ReaderIterator(reader).iterateSearchTree(data -> data.getMostSpecificId().ifPresent(callback));
    }

    public static class LookupResult
    {
        private final Country country;
        private final City city;
        private final Country registeredCountry;
        private final Continent continent;

        @MaxMindDbConstructor
        public LookupResult(@MaxMindDbParameter(name = "continent") Continent continent,
                            @MaxMindDbParameter(name = "registered_country") Country registeredCountry,
                            @MaxMindDbParameter(name = "country") Country country,
                            @MaxMindDbParameter(name = "city") City city)
        {
            this.continent = continent;
            this.registeredCountry = registeredCountry;
            this.country = country;
            this.city = city;
        }

        public Optional<Integer> getMostSpecificId()
        {
            final Optional<Integer> cityId = Optional.ofNullable(city != null ? city.getId() : null);
            final Optional<Integer> countryId = Optional.ofNullable(country != null ? country.getId() : null);
            final Optional<Integer> regCountryId = Optional.ofNullable(registeredCountry != null ? registeredCountry.getId() : null);
            final Optional<Integer> continentId = Optional.ofNullable(continent != null ? continent.getId() : null);
            return cityId.or(() -> countryId).or(() -> regCountryId).or(() -> continentId);
        }

        public Country getCountry()
        {
            return country;
        }

        public Country getRegisteredCountry()
        {
            return registeredCountry;
        }

        public City getCity()
        {
            return city;
        }

        public static class Continent
        {
            private final long id;

            @MaxMindDbConstructor
            public Continent(@MaxMindDbParameter(name = "geoname_id") long id)
            {
                this.id = id;
            }

            public int getId()
            {
                return Ints.checkedCast(id);
            }
        }

        public static class Country
        {
            private final long id;

            @MaxMindDbConstructor
            public Country(@MaxMindDbParameter(name = "geoname_id") long id)
            {
                this.id = id;
            }

            public int getId()
            {
                return Ints.checkedCast(id);
            }
        }

        public static class City
        {
            private final long id;
            private final long radius;

            @MaxMindDbConstructor
            public City(@MaxMindDbParameter(name = "geoname_id") long id)
            {
                this.id = id;
                this.radius = -1;
            }

            @MaxMindDbConstructor
            public City(@MaxMindDbParameter(name = "geoname_id") long id, @MaxMindDbParameter(name = "accuracy_radius") long radius)
            {
                this.id = id;
                this.radius = radius;
            }

            public int getId()
            {
                return Ints.checkedCast(id);
            }

            public int getRadius()
            {
                return Ints.checkedCast(radius);
            }
        }
    }
}
