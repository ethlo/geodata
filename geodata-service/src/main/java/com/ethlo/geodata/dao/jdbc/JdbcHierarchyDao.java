package com.ethlo.geodata.dao.jdbc;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Repository;

import com.ethlo.geodata.importer.jdbc.MysqlCursorUtil;

@Repository
public class JdbcHierarchyDao extends JdbcBaseDao
{
    private int loadExplicitHierarchy(final Map<Integer, Integer> childToParent) throws SQLException
    {
        final AtomicInteger explicitCount = new AtomicInteger();
        final String sql = "SELECT id, parent_id FROM geohierarchy";
        new MysqlCursorUtil(dataSource).query(sql, Collections.emptyMap(), rs ->
        {
            while (rs.next())
            {
                final int id = rs.getInt("id");
                final int parentId = rs.getInt("parent_id");
                if (childToParent.putIfAbsent(id, parentId) == null)
                {
                    explicitCount.incrementAndGet();
                }
            }
        });
        return explicitCount.get();
    }

}
