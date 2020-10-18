package com.ethlo.geodata.dao;

import java.util.Optional;

public interface BoundaryDao
{
    Optional<byte[]> findById(int id);
}
