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
 * Benchmark:
 *  1. Generate a large orders table and load it into MySQL/MariaDB.
 *  2. Benchmark the same aggregation through
 *     (a) the ODBC extension, (b) the native MySQL scanner extension, and
 *     (c) a local DuckDB table as the no-transfer baseline.
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
     * Generates `rows` orders into a local DuckDB table (which doubles as the
     * baseline for the native benchmark), stages them as CSV, and bulk-loads
     * them into MySQL with odbc_copy. Row-by-row INSERTs over ODBC would take
     * forever at this scale; odbc_copy batches up to 2048 rows per round trip
     * and wraps the whole load in a remote transaction.
     *
     * The CSV detour exists because odbc_copy runs its source query in a
     * separate DuckDB instance and cannot see orders_local directly.
     * The CSV column order must match the remote table column order.
     */
    public Map<String, Object> generateAndLoadOrders(long rows) {
        Map<String, Object> report = new LinkedHashMap<>();
        String csvPath = System.getProperty("java.io.tmpdir") + "/orders.csv";

        // 1. Generate into a local table: the native benchmark scans this.
        long t0 = System.nanoTime();
        jdbc.execute("""
                CREATE OR REPLACE TABLE orders_local AS
                SELECT range AS id,
                       (range %% 1000)::INTEGER AS customer_id,
                       round(random() * 100, 2) AS amount
                FROM range(%d)
                """.formatted(rows));
        report.put("generate_seconds", secondsSince(t0));

        // 2. Stage as CSV for odbc_copy.
        long t1 = System.nanoTime();
        jdbc.execute("COPY orders_local TO '%s' (FORMAT CSV, HEADER true)"
                .formatted(csvPath));
        report.put("stage_csv_seconds", secondsSince(t1));

        // 3. Remote DDL goes through odbc_query (a table function, hence FROM ...).
        //    Explicit CREATE TABLE because create_table=true fails for
        //    MySQL/MariaDB ("column type not recognized: DUCKDB_TYPE_BIGINT").
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

        // 4. Bulk-load over ODBC. column_quotes: MariaDB's default sql_mode
        //    rejects double-quoted identifiers, so quote with backticks.
        long t2 = System.nanoTime();
        jdbc.execute("""
                FROM odbc_copy(getvariable('conn'),
                    source_file = '%s',
                    dest_table = 'orders',
                    batch_size = 2048,
                    column_quotes = '`')
                """.formatted(csvPath));
        report.put("mysql_load_seconds", secondsSince(t2));
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

    /**
     * Same aggregation on the local DuckDB table: the no-transfer baseline.
     * Same rows as MySQL holds (loaded from the same generation), so the
     * top-5 output matches the other two paths for a given load.
     */
    public Map<String, Object> benchmarkNative() {
        long t0 = System.nanoTime();
        List<Map<String, Object>> result = jdbc.queryForList("""
                SELECT customer_id, sum(amount) AS total
                FROM orders_local
                GROUP BY customer_id
                ORDER BY total DESC
                LIMIT 5
                """);
        return report("native_duckdb", secondsSince(t0), result);
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