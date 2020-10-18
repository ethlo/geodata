package com.ethlo.geodata.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ethlo.geodata.MapFeature;
import com.ethlo.geodata.model.Country;
import com.ethlo.geodata.model.RawLocation;
import com.ethlo.geodata.progress.StepProgressListener;

public interface LocationDao
{
    Map<String, Integer> loadAdminLevels(Map<Integer, MapFeature> featureCodes, StepProgressListener listener);

    Map<Integer, Integer> processChildToParent(StepProgressListener listener, Map<String, Country> countryToId, Map<Integer, MapFeature> featureCodes, Map<String, Integer> adminLevels, Map<String, Integer> reverseFeatureMap, int adminLevelCount);

    Page<RawLocation> findChildren(String countryCode, int adm1FeatureCodeId, Pageable pageable);

    Optional<RawLocation> findById(int id);

    Page<RawLocation> findCountriesOnContinent(String continentCode, Pageable pageable);

    List<RawLocation> findCountries();

    List<Integer> findByPhoneNumber(String phoneNumber);
}
