package org.example.duckdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Sample 1: the row-at-a-time baseline.
 *
 * This is the classic JDBC loop from the blog post: ResultSet hands you
 * one row at a time (next()) and one value at a time (getLong(1), ...).
 * For every cell, the driver must locate the value inside the current
 * native chunk, convert it, and hand it across the JNI boundary -- the
 * "row-at-a-time tax" that the chunked API avoids.
 *
 * This path remains the right default for most queries; run it here so
 * you can compare it directly against S02_ChunkedResult on the same data.
 */
public final class S01_ResultSetBaseline {

    static final int ROWS = 10_000_000;

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {

            createMeasurements(conn);

            long start = System.nanoTime();
            long count = 0;
            double sum = 0;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT a, b FROM measurements")) {
                while (rs.next()) {                 // one row at a time
                    long a = rs.getLong(1);         // one value at a time
                    double b = rs.getDouble(2);
                    count++;
                    sum += a + b;
                }
            }

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("ResultSet: read %,d rows in %,d ms (checksum=%.1f)%n",
                    count, elapsedMs, sum);
        }
    }

    /** Shared test data: ROWS rows of (BIGINT, DOUBLE). */
    static void createMeasurements(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE measurements AS " +
                    "SELECT range AS a, range * 0.5 AS b FROM range(" + ROWS + ")");
        }
    }

    private S01_ResultSetBaseline() {}
}
