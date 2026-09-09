package org.example.duckdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.duckdb.DuckDBConnection;

public final class S01_ScalarVarArgs1 {

    public static void main(String[] args) throws Exception {
        DuckDBConnection conn = (DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:");
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE tbl (x INTEGER, y DOUBLE, s VARCHAR)");
            // using try-with-resources to automatically close the appender at the end of the scope
            try (var appender = conn.createAppender(DuckDBConnection.DEFAULT_SCHEMA, "tbl")) {
                appender.beginRow();
                appender.append(10);
                appender.append(3.2);
                appender.append("hello");
                appender.endRow();
                appender.beginRow();
                appender.append(20);
                appender.append(-8.1);
                appender.append("world");
                appender.endRow();
            }
        }

    }

}
