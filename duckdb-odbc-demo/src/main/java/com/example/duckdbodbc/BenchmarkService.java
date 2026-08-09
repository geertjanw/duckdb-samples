package com.example.duckdbodbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stretch goals:
 *  1. Generate a large orders table and load it into MySQL/MariaDB.
 *  2. Benchmark the same aggregation through
 *     (a) the ODBC extension and (b) the native MySQL scanner extension.
 *
 * Uses the DuckDB core `odbc_scanner` extension API:
 *  - odbc_query(conn, sql)  -- table function; runs any SQL (incl. DDL) in the remote DB
 *  - odbc_copy(conn, ...)   -- bulk-loads rows into the remote DB over ODBC,
 *                              batching inserts (up to 2048 rows per SQLExecute)
 * Note: there is no odbc_exec in this extension; DDL also goes through odbc_query.
 *
 * MariaDB/MySQL specifics encountered along the way:
 *  - odbc_copy's create_table=true fails ("column type not recognized:
 *    DUCKDB_TYPE_BIGINT"): the automatic type mapping is incomplete for
 *    MySQL/MariaDB (Tier 2 support). We create the table explicitly instead,
 *    which also lets us keep the PRIMARY KEY.
 *  - odbc_copy quotes column names with double quotes by default, which
 *    MariaDB rejects in its default sql_mode (it expects backticks), hence
 *    column_quotes = '`'.
 */
@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    private final JdbcTemplate jdbc;
    private final OdbcQueryService odbc;
    private final String scannerConnectionString;

    private volatile boolean mysqlScannerAttached = false;

    public BenchmarkService(JdbcTemplate jdbc,
                            OdbcQueryService odbc,
                            @Value("${app.mysql.scanner-connection-string}") String scannerConnectionString) {
        this.jdbc = jdbc;
        this.odbc = odbc;
        this.scannerConnectionString = scannerConnectionString;
    }

    /**
     * Generates `rows` orders in DuckDB, exports them to CSV, and bulk-loads
     * them into MySQL with odbc_copy. Row-by-row INSERTs over ODBC would take
     * forever at this scale; odbc_copy batches up to 2048 rows per round trip
     * and wraps the whole load in a remote transaction.
     * The CSV column order must match the table column order.
     */
    public Map<String, Object> generateAndLoadOrders(long rows) {
        Map<String, Object> report = new LinkedHashMap<>();
        String csvPath = System.getProperty("java.io.tmpdir") + "/orders.csv";

        long t0 = System.nanoTime();
        jdbc.execute("""
                COPY (SELECT range AS id,
                             (range %% 1000)::INTEGER AS customer_id,
                             round(random() * 100, 2) AS amount
                      FROM range(%d))
                TO '%s' (FORMAT CSV, HEADER true)
                """.formatted(rows, csvPath));
        report.put("generate_csv_seconds", secondsSince(t0));

        // DDL goes through odbc_query (a table function, hence FROM ...).
        jdbc.execute("""
                FROM odbc_query(getvariable('conn'),
                    'DROP TABLE IF EXISTS orders')
                """);
        jdbc.execute("""
                FROM odbc_query(getvariable('conn'),
                    'CREATE TABLE orders (
                         id BIGINT PRIMARY KEY,
                         customer_id INT,
                         amount DOUBLE)')
                """);

        long t1 = System.nanoTime();
        jdbc.execute("""
                FROM odbc_copy(getvariable('conn'),
                    source_file = '%s',
                    dest_table = 'orders',
                    batch_size = 2048,
                    column_quotes = '`')
                """.formatted(csvPath));
        report.put("mysql_load_seconds", secondsSince(t1));
        report.put("rows", rows);
        log.info("Loaded {} rows into MySQL: {}", rows, report);
        return report;
    }

    /** Aggregation pushed through the ODBC extension (row-by-row transfer). */
    public Map<String, Object> benchmarkOdbc() {
        long t0 = System.nanoTime();
        List<Map<String, Object>> result = jdbc.queryForList("""
                SELECT customer_id, sum(amount) AS total
                FROM odbc_query(getvariable('conn'),
                     'SELECT customer_id, amount FROM orders')
                GROUP BY customer_id
                ORDER BY total DESC
                LIMIT 5
                """);
        return report("odbc_extension", secondsSince(t0), result);
    }

    /** Same aggregation through the native MySQL scanner extension. */
    public Map<String, Object> benchmarkMySqlScanner() {
        attachMySqlScanner();
        long t0 = System.nanoTime();
        List<Map<String, Object>> result = jdbc.queryForList("""
                SELECT customer_id, sum(amount) AS total
                FROM mysqldb.orders
                GROUP BY customer_id
                ORDER BY total DESC
                LIMIT 5
                """);
        return report("mysql_scanner", secondsSince(t0), result);
    }

    private synchronized void attachMySqlScanner() {
        if (mysqlScannerAttached) {
            return;
        }
        jdbc.execute("INSTALL mysql");
        jdbc.execute("LOAD mysql");
        jdbc.execute("ATTACH '%s' AS mysqldb (TYPE mysql, READ_ONLY)"
                .formatted(scannerConnectionString));
        mysqlScannerAttached = true;
        log.info("MySQL scanner attached.");
    }

    private static double secondsSince(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 1e7) / 100.0;
    }

    private static Map<String, Object> report(String path, double seconds,
                                              List<Map<String, Object>> sample) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", path);
        m.put("seconds", seconds);
        m.put("top5", sample);
        return m;
    }
}