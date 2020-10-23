/*-
 * #%L
 * geodata-fast-server
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.rapidoid.http.Resp;
import org.rapidoid.log.Log;
import org.rapidoid.setup.App;
import org.rapidoid.setup.My;
import org.rapidoid.setup.On;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;

import com.ethlo.geodata.GeodataService;
import com.ethlo.geodata.GeodataServiceImpl;
import com.ethlo.geodata.dao.CountryDao;
import com.ethlo.geodata.dao.FeatureCodeDao;
import com.ethlo.geodata.dao.HierarchyDao;
import com.ethlo.geodata.dao.IpDao;
import com.ethlo.geodata.dao.LocationDao;
import com.ethlo.geodata.dao.TimeZoneDao;
import com.ethlo.geodata.dao.file.FileCountryDao;
import com.ethlo.geodata.dao.file.FileFeatureCodeDao;
import com.ethlo.geodata.dao.file.FileHierarchyDao;
import com.ethlo.geodata.dao.file.FileIpDao;
import com.ethlo.geodata.dao.file.FileMmapLocationDao;
import com.ethlo.geodata.dao.file.FileTimeZoneDao;
import com.ethlo.geodata.model.GeoLocation;
import com.ethlo.geodata.model.GeoLocationWithPath;
import com.ethlo.geodata.progress.StatefulProgressListener;
import com.ethlo.geodata.util.JsonUtil;
import com.google.common.collect.Lists;

public class Server
{
    private final GeodataService geodataService;

    public Server(String[] args)
    {
        final Path basePath = Paths.get("/tmp/geodata");
        final LocationDao locationDao = new FileMmapLocationDao(basePath);
        final IpDao ipDao = new FileIpDao(basePath);
        final HierarchyDao hierarchyDao = new FileHierarchyDao(basePath);
        final FeatureCodeDao featureCodeDao = new FileFeatureCodeDao(basePath);
        final TimeZoneDao timeZoneDao = new FileTimeZoneDao(basePath);
        final CountryDao countryDao = new FileCountryDao(basePath);
        final int boundaryQualityConstant = 200_000;
        this.geodataService = new GeodataServiceImpl(locationDao, ipDao, hierarchyDao, featureCodeDao, timeZoneDao, countryDao, Collections.emptyList(), boundaryQualityConstant);
        final StatefulProgressListener progressListener = new StatefulProgressListener();
        geodataService.load(progressListener);

        App.init(args, "mode=production");
        Log.info("Starting application");

        My.errorHandler((req, resp, error) -> errorResponse(resp, 500, "Internal error"));
        My.objectMapper(JsonUtil.getMapper());

        On.get("/v1/locations/{id}").json((Integer id) ->
                handleError(() -> withPath(geodataService.findById(id))));

        On.get("/v1/locations/ip/{ip}").json((String ip) ->
                handleError(() -> withPath(geodataService.findByIp(ip))));

        On.get("/v1/locations/name/{name}").json((String name, Integer page, Integer page_size) ->
                geodataService.findByName(name, PageRequest.of(page != null ? page : 0, page_size != null ? page_size : 25)).map(this::withPath));

        /*
        // Dummy login: successful if the username is the same as the password, or a proper password is entered
        My.loginProvider((req, username, password) -> username.equals(password) || Auth.login(username, password));

        // Gives the 'manager' role to every logged-in user except 'admin'
        My.rolesProvider((req, username) -> U.eq(username, "admin") ? Auth.getRolesFor(username) : U.set("manager"));
         */

        App.ready(); // now everything is ready, so start the application
    }

    public static void main(String[] args)
    {
        new Server(args);
    }

    private Object handleError(final Supplier<Object> call)
    {
        try
        {
            return call.get();
        }
        catch (EmptyResultDataAccessException exc)
        {
            return new ApiError(404, exc.getMessage());
        }
    }

    private Object errorResponse(final Resp resp, final int statusCode, final String message)
    {
        return resp.json(new ApiError(statusCode, message));
    }

    private GeoLocationWithPath withPath(final GeoLocation location)
    {
        final List<GeoLocation> path = Lists.reverse(geodataService.findPath(location.getId()));
        return new GeoLocationWithPath(location, path);
    }
}
