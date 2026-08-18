package org.example.duckdb;

import java.sql.DriverManager;

import org.duckdb.DuckDBChunkedResult;
import org.duckdb.DuckDBConnection;
import org.duckdb.DuckDBDataChunkReader;
import org.duckdb.DuckDBPreparedStatement;
import org.duckdb.DuckDBReadableVector;

public class S03_ChunkedResult {

    public static void main(String[] args) throws Exception {
        try (DuckDBConnection conn = DriverManager
                .getConnection("jdbc:duckdb:")
                .unwrap(DuckDBConnection.class);
             DuckDBPreparedStatement ps = conn.prepare("SELECT ? AS col1")) {

            ps.setInt(1, 42); // statement parameters are still 1-based

            try (DuckDBChunkedResult res = ps.query()) {

                // advance to the next chunk, returns true on success
                while (res.nextChunk()) {

                    // get the current chunk from the result
                    DuckDBDataChunkReader chunk = res.chunk();

                    // iterate over the chunk columns, all indices are 0-based
                    for (long col = 0; col < chunk.columnCount(); col++) {

                        // get a vector for the specified column
                        DuckDBReadableVector vector = chunk.vector(col);

                        // iterate over vector rows
                        for (long row = 0; row < chunk.rowCount(); row++) {
                            int val = vector.getInt(row);
                            System.out.println(val);
                        }
                    }
                }
            }
        }
    }
}