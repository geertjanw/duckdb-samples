package com.example.recon;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

final class Db {
    static Connection open(Path drops) throws SQLException {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("DuckDB driver missing", e);
        }
        Connection c = DriverManager.getConnection("jdbc:duckdb:");
        try (Statement s = c.createStatement()) {
            s.execute("CREATE VIEW orders AS SELECT * FROM '" + drops + "/orders_*.jsonl'");
            s.execute("CREATE VIEW payouts AS SELECT * FROM '" + drops + "/stripe_*.csv'");
            s.execute("CREATE VIEW shipments AS SELECT * FROM '" + drops + "/ship_*.parquet'");
        }
        return c;
    }
}
