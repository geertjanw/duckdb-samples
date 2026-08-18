package org.example.duckdb;

import java.sql.DriverManager;
import java.sql.Statement;

import org.duckdb.DuckDBChunkedResult;
import org.duckdb.DuckDBConnection;
import org.duckdb.DuckDBDataChunkReader;
import org.duckdb.DuckDBPreparedStatement;
import org.duckdb.DuckDBReadableVector;

/**
 * Sample 2: reading a query result as lazily fetched columnar data chunks.
 *
 * This is the main example from the blog post. Instead of the row-at-a-time
 * ResultSet, the query result is a sequence of data chunks (batches of
 * column vectors, up to 2048 rows) pulled one at a time from the engine --
 * the Java surface of the C API's duckdb_fetch_chunk.
 *
 * Key points, mirrored from the post:
 *  - It is LAZY: nextChunk() fetches one chunk at a time; the full result
 *    is never materialized on the Java side.
 *  - It is COLUMNAR: inside a chunk you read vector by vector, in tight
 *    loops, instead of bouncing between columns on every row.
 *  - It is STRICT about types: unlike ResultSet.getDouble(), which coerces
 *    (e.g. DECIMAL to double), vector.getDouble() requires the vector to
 *    actually be DOUBLE and throws otherwise. Match the getter to the
 *    column's DuckDB type, or CAST in SQL. Beware: a literal like 0.5 is
 *    DECIMAL in DuckDB, so "range * 0.5" yields a DECIMAL column -- hence
 *    the explicit CAST below.
 *  - WATCH YOUR INDICES: statement parameters are 1-based (JDBC tradition);
 *    chunk columns and rows are 0-based (matching the C API).
 *  - Limitations (driver 1.5.5.x): basic data types only (LIST/STRUCT
 *    planned), and query() exists on prepared statements only -- there is
 *    no query(String) overload yet.
 */
public final class S02_ChunkedResult {

    static final int ROWS = 10_000_000;

    public static void main(String[] args) throws Exception {
        // DuckDB-specific APIs are reached by unwrapping the JDBC connection.
        try (DuckDBConnection conn = DriverManager
                .getConnection("jdbc:duckdb:")
                .unwrap(DuckDBConnection.class)) {

            // Test data: ROWS rows of (BIGINT, DOUBLE). The CAST matters --
            // see the "STRICT about types" note above.
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE measurements AS " +
                        "SELECT range AS a, CAST(range * 0.5 AS DOUBLE) AS b " +
                        "FROM range(" + ROWS + ")");
            }

            long start = System.nanoTime();
            long count = 0;
            double sum = 0;
            int chunks = 0;

            // query() is available on prepared statements only.
            try (DuckDBPreparedStatement ps =
                         conn.prepare("SELECT a, b FROM measurements");
                 DuckDBChunkedResult res = ps.query()) {

                // advance to the next chunk, returns true on success
                while (res.nextChunk()) {
                    chunks++;

                    // get the current chunk from the result
                    DuckDBDataChunkReader chunk = res.chunk();
                    long rows = chunk.rowCount();
                    count += rows;

                    // read column by column, in tight per-vector loops
                    // (all chunk indices are 0-based)
                    DuckDBReadableVector aVec = chunk.vector(0);
                    for (long row = 0; row < rows; row++) {
                        sum += aVec.getLong(row);
                    }
                    DuckDBReadableVector bVec = chunk.vector(1);
                    for (long row = 0; row < rows; row++) {
                        sum += bVec.getDouble(row);
                    }
                }
            }

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.printf(
                    "ChunkedResult: read %,d rows in %,d chunks in %,d ms (checksum=%.1f)%n",
                    count, chunks, elapsedMs, sum);
        }
    }

    private S02_ChunkedResult() {}
}