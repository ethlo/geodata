package com.ethlo.geodata.importer.jdbc;

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