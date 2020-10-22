package com.ethlo.geodata.dao;

import java.util.Map;

public interface HierarchyDao
{
    void save(Map<Integer, Integer> childToParent);

    Map<Integer, Integer> load();
}
