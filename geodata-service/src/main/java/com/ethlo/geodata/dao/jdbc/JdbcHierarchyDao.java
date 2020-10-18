package com.ethlo.geodata.dao.jdbc;

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
