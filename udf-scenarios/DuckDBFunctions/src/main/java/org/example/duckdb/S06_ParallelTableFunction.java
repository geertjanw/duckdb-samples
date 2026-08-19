package org.example.duckdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.duckdb.DuckDBColumnType;
import org.duckdb.DuckDBDataChunkWriter;
import org.duckdb.DuckDBFunctions;
import org.duckdb.DuckDBTableFunction;
import org.duckdb.DuckDBTableFunctionBindInfo;
import org.duckdb.DuckDBTableFunctionCallInfo;
import org.duckdb.DuckDBTableFunctionInitInfo;
import org.duckdb.DuckDBValue;

/**
 * Sample 6: a table function that runs on multiple threads.
 *
 * Two things enable parallel execution:
 *   1. info.setMaxThreads(n) in init() tells DuckDB how many threads may run
 *      the scan.
 *   2. preserve_insertion_order = false lets the engine emit rows in any
 *      order (a prerequisite for parallel output). It is set here through the
 *      connection properties; it can also go in the connection string.
 *
 * apply() is then called concurrently from several threads on the same global
 * state, so that state must be thread-safe. This function generates the
 * numbers [0, TOTAL) by having each apply() call atomically claim a block of
 * row numbers from a shared AtomicLong cursor -- getAndAdd is atomic, so no
 * number is produced twice or skipped, no matter how the threads interleave.
 * The program checks that by summing the output and comparing it to the
 * closed-form 0 + 1 + ... + (TOTAL-1).
 */
public final class S06_ParallelTableFunction {

    static final long TOTAL = 5_000_000;

    /** Global init state shared across worker threads: the next unclaimed row. */
    static final class Cursor {
        final long total;
        final AtomicLong next = new AtomicLong(0);

        Cursor(long total) {
            this.total = total;
        }
    }

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        // Required for parallel output; without it rows must keep insertion order.
        props.setProperty("preserve_insertion_order", "false");

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:", props)) {

            DuckDBFunctions.tableFunction()
                    .withName("java_parallel_range")
                    .withParameter(long.class) // how many numbers to generate
                    .withFunction(new DuckDBTableFunction<Long, Cursor, Object>() {
                        @Override
                        public Long bind(DuckDBTableFunctionBindInfo info) throws Exception {
                            info.addResultColumn("n", DuckDBColumnType.BIGINT);
                            DuckDBValue param = info.getParameter(0);
                            return param.getLong();
                        }

                        @Override
                        public Cursor init(DuckDBTableFunctionInitInfo info) throws Exception {
                            info.setMaxThreads(4); // allow up to 4 worker threads
                            return new Cursor(info.<Long>getBindData());
                        }

                        @Override
                        public long apply(DuckDBTableFunctionCallInfo info,
                                          DuckDBDataChunkWriter output) throws Exception {
                            Cursor cursor = info.getInitData();
                            long capacity = output.capacity();
                            // Atomically claim a block of up to `capacity` numbers.
                            long start = cursor.next.getAndAdd(capacity);
                            if (start >= cursor.total) {
                                return 0;
                            }
                            long count = Math.min(capacity, cursor.total - start);
                            for (long i = 0; i < count; i++) {
                                output.vector(0).setLong(i, start + i);
                            }
                            return count;
                        }
                    })
                    .register(conn);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT count(*) AS rows, sum(n) AS total FROM java_parallel_range(" + TOTAL + ")")) {
                rs.next();
                long rows = rs.getLong("rows");
                long total = rs.getLong("total");
                long expected = TOTAL * (TOTAL - 1) / 2; // 0 + 1 + ... + (TOTAL-1)
                System.out.printf("rows=%,d  sum=%,d  expected=%,d  ->  %s%n",
                        rows, total, expected,
                        (rows == TOTAL && total == expected) ? "OK (no rows lost or duplicated)" : "MISMATCH");
            }
        }
    }

    private S06_ParallelTableFunction() {}
}
