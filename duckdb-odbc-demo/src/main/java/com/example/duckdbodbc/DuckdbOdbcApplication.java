package com.example.duckdbodbc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Entry point. DataSource auto-configuration is disabled because we manage a
 * single, long-lived DuckDB connection ourselves (see {@link DuckDbConfig}):
 * the ODBC connection handle created with SET VARIABLE is scoped to one DuckDB
 * connection, so a regular connection pool would "lose" it between requests.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class DuckdbOdbcApplication {

    public static void main(String[] args) {
        SpringApplication.run(DuckdbOdbcApplication.class, args);
    }
}
