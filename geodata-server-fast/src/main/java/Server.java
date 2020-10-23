import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.rapidoid.log.Log;
import org.rapidoid.setup.App;
import org.rapidoid.setup.On;
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

        App.init(args, "secret=YOUR-SECRET");
        Log.info("Starting application");

        final App app = new App();
        //app.beans(new MyCtrl()); // provide beans (controllers, services etc.)

        On.get("/v1/locations/{id}").json((Integer id) ->
                withPath(geodataService.findById(id)));

        On.get("/v1/locations/ip/{ip}").json((String ip) ->
                withPath(geodataService.findByIp(ip)));

        On.get("/v1/locations/name/{name}").json((String name, Integer page, Integer page_size) ->
                geodataService.findByName(name, PageRequest.of(page != null ? page : 0, page_size != null ? page_size : 25)).map(this::withPath));

        /*

        /*
        On.post("/books").json((@Valid Book book) -> {
            // TODO insert new book
            return book;
        });

        On.put("/books/{id}").json((Long id, @Valid Book book) -> {
            // TODO update/replace book
            return true;
        });

        On.delete("/books/{id}").json((Long id) -> {
            // TODO delete book
            return true;
        });

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

    private GeoLocationWithPath withPath(final GeoLocation location)
    {
        final List<GeoLocation> path = Lists.reverse(geodataService.findPath(location.getId()));
        return new GeoLocationWithPath(location, path);
    }
}