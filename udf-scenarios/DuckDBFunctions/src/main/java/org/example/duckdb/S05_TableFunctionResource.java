package org.example.duckdb;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * Sample 5: a table function that holds an open resource -- here a file
 * reader -- and streams its rows.
 *
 * The global init state ({@link ReaderState}) owns a BufferedReader opened in
 * init(). apply() fills each output chunk (up to capacity() rows) with lines,
 * and when readLine() hits end-of-file it closes the reader and returns 0,
 * which stops the scan.
 *
 * NOTE ON DETERMINISTIC CLEANUP
 * -----------------------------
 * A future driver adds a DuckDBTableFunctionState interface whose close() is
 * called deterministically by DuckDB once the scan finishes -- also after a
 * LIMIT, an error, cancellation, or an early JDBC close -- which is the
 * robust way to release such resources. That interface is not present in the
 * driver pinned here (1.5.5.1), so this sample closes the reader itself on
 * end-of-file. The catch: on a query like `... LIMIT 1` the engine may stop
 * calling apply() before EOF, and then this hand-rolled close never runs.
 * Once DuckDBTableFunctionState ships, have ReaderState implement it and move
 * the resource.close() call into its close() method.
 */
public final class S05_TableFunctionResource {

    /** Global init state: owns the open reader and the running line number. */
    static final class ReaderState {
        final BufferedReader reader;
        final AtomicLong lineNo = new AtomicLong(0);
        final AtomicBoolean done = new AtomicBoolean(false);

        ReaderState(BufferedReader reader) {
            this.reader = reader;
        }
    }

    public static void main(String[] args) throws Exception {
        // A small on-disk file to stream through the table function.
        Path file = Files.createTempFile("java_read_lines", ".txt");
        Files.write(file, List.of("alpha", "bravo", "charlie", "delta", "echo"));
        file.toFile().deleteOnExit();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {

            DuckDBFunctions.tableFunction()
                    .withName("java_read_lines")
                    .withParameter(String.class) // path to the file to read
                    .withFunction(new DuckDBTableFunction<Path, ReaderState, Object>() {
                        @Override
                        public Path bind(DuckDBTableFunctionBindInfo info) throws Exception {
                            info.addResultColumn("line_no", DuckDBColumnType.BIGINT)
                                .addResultColumn("text", DuckDBColumnType.VARCHAR);
                            DuckDBValue param = info.getParameter(0);
                            return Path.of(param.getString());
                        }

                        @Override
                        public ReaderState init(DuckDBTableFunctionInitInfo info) throws Exception {
                            Path path = info.getBindData();
                            return new ReaderState(Files.newBufferedReader(path));
                        }

                        @Override
                        public long apply(DuckDBTableFunctionCallInfo info,
                                          DuckDBDataChunkWriter output) throws Exception {
                            ReaderState state = info.getInitData();
                            if (state.done.get()) {
                                return 0; // already exhausted and closed on a previous call
                            }
                            long capacity = output.capacity();
                            long written = 0;
                            while (written < capacity) {
                                String line = state.reader.readLine();
                                if (line == null) {
                                    // End of file: release the resource and stop.
                                    // (This call may still return the rows read
                                    // above, so the flag guards the next call.)
                                    state.done.set(true);
                                    state.reader.close();
                                    break;
                                }
                                output.vector(0).setLong(written, state.lineNo.incrementAndGet());
                                output.vector(1).setString(written, line);
                                written++;
                            }
                            return written;
                        }
                    })
                    .register(conn);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "FROM java_read_lines('" + file.toString().replace("'", "''") + "')")) {
                while (rs.next()) {
                    System.out.println(rs.getLong("line_no") + ": " + rs.getString("text"));
                }
            }
        }
    }

    private S05_TableFunctionResource() {}
}
