package com.ethlo.geodata.importer.jdbc;

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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Consumer;

import javax.sql.DataSource;

public class MysqlCursorUtil
{
    private final DataSource dataSource;

    public MysqlCursorUtil(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    public void query(String sql, Map<Integer, Object> params, Consumer<ResultSet> resultSetConsumer) throws SQLException
    {
        ResultSet rs = null;
        try (final Connection conn = dataSource.getConnection(); final PreparedStatement pstmt = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY))
        {
            pstmt.setFetchSize(Integer.MIN_VALUE);
            for (Map.Entry<Integer, Object> e : params.entrySet())
            {
                pstmt.setObject(e.getKey(), e.getValue());
            }
            rs = pstmt.executeQuery();
            resultSetConsumer.accept(rs);
        } finally
        {
            if (rs != null && !rs.isClosed())
            {
                rs.close();
            }
        }
    }
}
