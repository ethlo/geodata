package com.ethlo.geodata.importer;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import com.ethlo.geodata.util.Kml2GeoJson;
import com.ethlo.geodata.util.ResourceUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class GeoFabrikBoundaryImporter
{
    private final String baseUrl = "http://download.geofabrik.de/";
    private final Map<String, String> countries;
    private final Map<String, String> continents = new LinkedHashMap<>()
    {{
        put("AF", "africa");
        put("AS", "asia");
        put("EU", "europe");
        put("NA", "north-america");
        put("OC", "oceania");
        put("SA", "south-america");
        put("AN", "antarctica");
    }};

    public GeoFabrikBoundaryImporter(final Map<String, String> countries)
    {
        this.countries = countries;
    }

    public static void main(String[] args) throws IOException, XMLStreamException
    {
        final GeoFabrikBoundaryImporter importer = new GeoFabrikBoundaryImporter(Collections.singletonMap("NO", "Norway"));

        final ObjectNode geoJson = Kml2GeoJson.parse(new StringReader(importer.getKmlByCodes("Eu", "No")));
        System.out.println(geoJson);

        final ObjectNode geoJson2 = Kml2GeoJson.parse(new StringReader(importer.getKmlByNames("europe", "germany")));
        System.out.println(geoJson2);

        final ObjectNode geoJson3 = Kml2GeoJson.parse(new StringReader(importer.getKmlByNames("europe", "italy")));
        System.out.println(geoJson3);
    }

    public String getKmlByCodes(String continentCode, String countryCode) throws IOException
    {
        return getKmlByNames(getContinentName(continentCode), findCountryName(countryCode));
    }

    public String getKmlByNames(String continentName, String countryName) throws IOException
    {
        final String url = baseUrl + continentName + "/" + countryName + ".kml";
        final Map.Entry<Date, File> file = ResourceUtil.fetchResource((continentName + "_" + countryName + ".kml").toLowerCase(), url);
        return Files.readString(file.getValue().toPath());
    }

    private String getContinentName(final String continentCode)
    {
        return continents.get(continentCode.toUpperCase());
    }

    private String findCountryName(final String countryCode)
    {
        return countries.get(countryCode.toUpperCase()).toLowerCase();
    }
}
