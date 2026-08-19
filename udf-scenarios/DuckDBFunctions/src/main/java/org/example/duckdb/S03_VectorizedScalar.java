package org.example.duckdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.duckdb.DuckDBColumnType;
import org.duckdb.DuckDBFunctions;
import org.duckdb.DuckDBLogicalType;

/**
 * Sample 3: a vectorized scalar UDF that processes a whole batch at once.
 *
 * The scalar functions in samples 1 and 2 are invoked once per value. To
 * process a batch of rows in a single call, register a vectorized function
 * with withVectorizedFunction(). The callback receives:
 *   - a DuckDBDataChunkReader input  (up to 2048 rows), and
 *   - a DuckDBWritableVector  output (the result column).
 *
 * Read each input column with input.vector(columnIndex), iterate the row
 * indices with input.stream(), and write each result at the SAME row index.
 * This is the scalar-function counterpart to reading query results as
 * columnar data chunks (see the chunked-scenarios samples).
 *
 * The reader, vectors, and writer are valid only during callback execution
 * and must not be retained.
 */
public final class S03_VectorizedScalar {

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             DuckDBLogicalType tsType = DuckDBLogicalType.of(DuckDBColumnType.TIMESTAMP);
             DuckDBLogicalType strType = DuckDBLogicalType.of(DuckDBColumnType.VARCHAR);
             DuckDBLogicalType dblType = DuckDBLogicalType.of(DuckDBColumnType.DOUBLE)) {

            DuckDBFunctions.scalarFunction()
                    .withName("java_event_label")
                    .withParameters(tsType, strType, dblType)
                    .withReturnType(strType)
                    .withVectorizedFunction((input, output) ->
                            input.stream().forEach(row -> {
                                String value = input.vector(0).getLocalDateTime(row) + " | " +
                                        String.valueOf(input.vector(1).getString(row)).trim().toUpperCase() + " | " +
                                        input.vector(2).getDouble(row, 0.0d);
                                output.setString(row, value);
                            }))
                    .register(conn);

            // A few rows so the batch actually contains more than one value.
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT java_event_label(ts, name, score) AS label FROM (VALUES " +
                         "  (TIMESTAMP '2026-04-04 12:00:00', 'launch',  4.5)," +
                         "  (TIMESTAMP '2026-04-05 09:30:00', ' deploy', 9.0)," +
                         "  (TIMESTAMP '2026-04-06 18:15:00', 'rollout', 1.25)" +
                         ") AS t(ts, name, score)")) {
                while (rs.next()) {
                    System.out.println(rs.getString("label"));
                }
            }
        }
    }

    private S03_VectorizedScalar() {}
}
