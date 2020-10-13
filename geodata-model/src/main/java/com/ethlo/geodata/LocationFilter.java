package com.ethlo.geodata;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class LocationFilter
{
    private String name;
    private List<String> countryCodes = new LinkedList<>();
    private List<String> featureClasses = new LinkedList<>();
    private List<String> featureCodes = new LinkedList<>();

    public String getName()
    {
        return name;
    }

    public LocationFilter setName(String name)
    {
        this.name = name;
        return this;
    }

    public List<String> getCountryCodes()
    {
        return countryCodes;
    }

    public LocationFilter setCountryCodes(List<String> countryCodes)
    {
        this.countryCodes = toLowerCase(countryCodes);
        return this;
    }

    public List<String> getFeatureClasses()
    {
        return featureClasses;
    }

    public LocationFilter setFeatureClasses(List<String> featureClasses)
    {
        this.featureClasses = toLowerCase(featureClasses);
        return this;
    }

    public List<String> getFeatureCodes()
    {
        return featureCodes;
    }

    public LocationFilter setFeatureCodes(List<String> featureCodes)
    {
        this.featureCodes = toLowerCase(featureCodes);
        return this;
    }

    private List<String> toLowerCase(List<String> list)
    {
        if (list == null)
        {
            return Collections.emptyList();
        }
        else if (list.isEmpty())
        {
            return list;
        }
        return list.stream().map(String::toLowerCase).collect(Collectors.toList());
    }
}
