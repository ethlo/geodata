package com.ethlo.geodata.dao.jdbc;

import java.util.Collections;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ethlo.geodata.dao.BoundaryDao;
import com.ethlo.geodata.dao.jdbc.JdbcBaseDao;

@Repository
public class JdbcBoundaryDao extends JdbcBaseDao implements BoundaryDao
{
    @Override
    public Optional<byte[]> findById(final int id)
    {
        final String sql = "SELECT ST_AsBinary(raw_polygon) as wkb FROM geoboundaries WHERE id = :id";
        return jdbcTemplate.query(sql, Collections.singletonMap("id", id), rse ->
        {
            if (rse.next())
            {
                return Optional.of(rse.getBytes("wkb"));
            }
            return Optional.empty();
        });
    }
}
