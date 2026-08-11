package com.example.batchduckdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

/**
 * The DuckDB transformation step: the exact same aggregation as the Java demo,
 * expressed as one SQL statement and executed by DuckDB's vectorized engine.
 * DuckDB reads the CSV, groups, and writes the summary in a single pass, using
 * every core by default. Only the transform statement is timed.
 */
class DuckDbTransformTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(DuckDbTransformTasklet.class);

    private final String duckdbUrl;
    private final Path ordersCsv;
    private final Path summaryCsv;

    DuckDbTransformTasklet(String duckdbUrl, Path ordersCsv, Path summaryCsv) {
        this.duckdbUrl = duckdbUrl;
        this.ordersCsv = ordersCsv;
        this.summaryCsv = summaryCsv;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        String orders = ordersCsv.toAbsolutePath().toString().replace("'", "''");
        String summary = summaryCsv.toAbsolutePath().toString().replace("'", "''");

        // printf('%.2f', ...) formats the numeric aggregates to a fixed two
        // decimals so the output lines up with the Java demo's String.format.
        // Note: paths are injected via replace() rather than String.formatted()
        // precisely because the SQL itself contains printf format specifiers.
        String sql = """
                COPY (
                    SELECT customer_id,
                           category,
                           count(*)                             AS order_count,
                           printf('%.2f', sum(amount * quantity)) AS total_revenue,
                           sum(quantity)                        AS total_quantity,
                           printf('%.2f', avg(amount))          AS avg_amount,
                           printf('%.2f', max(amount * quantity)) AS max_revenue
                    FROM read_csv('__ORDERS__', header = true,
                         columns = {'id': 'BIGINT', 'customer_id': 'BIGINT',
                                    'category': 'BIGINT', 'quantity': 'BIGINT', 'amount': 'DOUBLE'})
                    GROUP BY customer_id, category
                    ORDER BY customer_id, category
                ) TO '__SUMMARY__' (FORMAT CSV, HEADER true)
                """
                .replace("__ORDERS__", orders)
                .replace("__SUMMARY__", summary);

        try (Connection conn = DriverManager.getConnection(duckdbUrl);
             Statement st = conn.createStatement()) {

            long threads = queryLong(st, "SELECT current_setting('threads')");
            long rows = queryLong(st,
                    "SELECT count(*) FROM read_csv('" + orders + "', header = true)");

            long start = System.nanoTime();
            st.execute(sql);
            double seconds = (System.nanoTime() - start) / 1e9;

            long groups = queryLong(st,
                    "SELECT count(*) FROM read_csv('" + summary + "', header = true)");

            log.info("DuckDB vectorized transform: {} rows -> {} groups in {} s on {} threads",
                    rows, groups, String.format(Locale.US, "%.2f", seconds), threads);
            System.out.printf(Locale.US,
                    "%n[duckdb] transformed %,d rows into %,d groups in %.2f s (%,.0f rows/s, %d threads)%n",
                    rows, groups, seconds, rows / seconds, threads);
        }
        return RepeatStatus.FINISHED;
    }

    private static long queryLong(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
