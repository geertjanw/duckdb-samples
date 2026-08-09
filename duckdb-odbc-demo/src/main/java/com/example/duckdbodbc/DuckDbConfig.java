package com.example.duckdbodbc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Wires up an embedded DuckDB instance behind a single shared JDBC connection.
 *
 * Why not a Hikari pool? The ODBC extension stores its connection handle in a
 * DuckDB *session variable* (SET VARIABLE conn = odbc_connect(...)). Session
 * variables live on one DuckDB connection. With a pool, each request could get
 * a different connection where the variable does not exist. A
 * SingleConnectionDataSource (suppressClose=true) keeps everything on one
 * connection, which is fine for a demo; for production you would open the ODBC
 * connection per statement or manage a small pinned pool.
 */
@Configuration
public class DuckDbConfig {

    @Bean(destroyMethod = "close")
    public Connection duckDbConnection(@Value("${app.duckdb.url}") String url) throws SQLException {
        return DriverManager.getConnection(url);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(Connection duckDbConnection) {
        return new JdbcTemplate(new SingleConnectionDataSource(duckDbConnection, true));
    }
}
