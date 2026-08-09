package com.example.duckdbodbc;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Uses the DuckDB ODBC extension (a.k.a. odbc_scanner) to query MySQL.
 *
 * Note the direction of the ODBC arrow:
 *  - DuckDB's ODBC *client/driver* lets other apps connect INTO DuckDB.
 *  - The ODBC *extension* (used here) lets DuckDB connect OUT to other
 *    databases through their ODBC drivers.
 */
@Service
public class OdbcQueryService {

    private static final Logger log = LoggerFactory.getLogger(OdbcQueryService.class);

    private final JdbcTemplate jdbc;
    private final String odbcConnectionString;
    private final String username;
    private final String password;

    public OdbcQueryService(JdbcTemplate jdbc,
                            @Value("${app.mysql.odbc-connection-string}") String odbcConnectionString,
                            @Value("${app.mysql.username}") String username,
                            @Value("${app.mysql.password}") String password) {
        this.jdbc = jdbc;
        this.odbcConnectionString = odbcConnectionString;
        this.username = username;
        this.password = password;
    }

    @PostConstruct
    void init() {
        log.info("Installing and loading DuckDB odbc extension...");
        jdbc.execute("INSTALL odbc");
        jdbc.execute("LOAD odbc");

        // Open the ODBC connection once and stash the handle in a session variable.
        jdbc.update("SET VARIABLE conn = odbc_connect(?, ?, ?)",
                odbcConnectionString, username, password);
        log.info("Connected to MySQL via ODBC.");
    }

    /** Run an arbitrary (read-only) SQL statement on MySQL through ODBC. */
    public List<Map<String, Object>> queryMySql(String mysqlSql) {
        return jdbc.queryForList(
                "SELECT * FROM odbc_query(getvariable('conn'), ?)", mysqlSql);
    }

    /** Simple demo query: top customers by revenue, computed inside MySQL. */
    public List<Map<String, Object>> topCustomers(int limit) {
        return queryMySql("""
                SELECT id, name, country, revenue
                FROM customers
                ORDER BY revenue DESC
                LIMIT %d""".formatted(limit));
    }

    /**
     * Demo of DuckDB's real superpower: pull rows from MySQL over ODBC and
     * aggregate them in DuckDB, in one SQL statement.
     */
    public List<Map<String, Object>> revenueByCountry() {
        return jdbc.queryForList("""
                SELECT country, count(*) AS customers, sum(revenue) AS total_revenue
                FROM odbc_query(getvariable('conn'),
                     'SELECT country, revenue FROM customers')
                GROUP BY country
                ORDER BY total_revenue DESC
                """);
    }
}
