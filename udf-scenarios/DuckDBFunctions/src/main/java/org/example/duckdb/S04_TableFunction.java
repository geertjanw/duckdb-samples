package org.example.duckdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

import org.duckdb.DuckDBDataChunkWriter;
import org.duckdb.DuckDBFunctions;
import org.duckdb.DuckDBTableFunction;
import org.duckdb.DuckDBTableFunctionBindInfo;
import org.duckdb.DuckDBTableFunctionCallInfo;
import org.duckdb.DuckDBTableFunctionInitInfo;
import org.duckdb.DuckDBValue;

/**
 * Sample 4: a basic table function that emits a fixed set of rows.
 *
 * A table function is built with DuckDBFunctions.tableFunction() and
 * implemented as a DuckDBTableFunction with three callbacks:
 *   - bind():  runs at prepare time. Declares the output columns with
 *              addResultColumn() and reads the call parameters. Its return
 *              value is the "bind data" passed to the later callbacks.
 *   - init():  runs once before execution and returns the global state.
 *   - apply(): writes rows into the output vectors and returns how many it
 *              wrote. The engine calls it repeatedly until it returns 0.
 *
 * The three generic parameters of DuckDBTableFunction are, in order, the
 * bind data, the global init data, and the local init data. Values are
 * written through a DuckDBDataChunkWriter: each output vector is addressed by
 * column index (0-based), each value by row index.
 */
public final class S04_TableFunction {

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {

            DuckDBFunctions.tableFunction()
                    .withName("java_table_basic")
                    .withParameter(int.class)
                    .withNamedParameter("param1", String.class)
                    .withFunction(new DuckDBTableFunction<Integer, AtomicBoolean, Object>() {
                        @Override
                        public Integer bind(DuckDBTableFunctionBindInfo info) throws Exception {
                            info.addResultColumn("col1", Integer.TYPE)
                                .addResultColumn("col2", String.class);
                            DuckDBValue param = info.getParameter(0);
                            return param.getInt();
                        }

                        @Override
                        public AtomicBoolean init(DuckDBTableFunctionInitInfo info) throws Exception {
                            // Emit the rows once, then signal "done" on the next call.
                            return new AtomicBoolean(false);
                        }

                        @Override
                        public long apply(DuckDBTableFunctionCallInfo info,
                                          DuckDBDataChunkWriter output) throws Exception {
                            Integer bindData = info.getBindData();
                            AtomicBoolean done = info.getInitData();
                            if (done.get()) {
                                return 0;
                            }
                            output.vector(0).setInt(0, bindData);
                            output.vector(1).setString(0, "foo");
                            output.vector(0).setNull(1);
                            output.vector(1).setString(1, "bar");
                            done.set(true);
                            return 2;
                        }
                    })
                    .register(conn);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "FROM java_table_basic(42, param1 = 'foobar')")) {
                while (rs.next()) {
                    int col1 = rs.getInt("col1");
                    System.out.println((rs.wasNull() ? "NULL" : col1) + ", " + rs.getString("col2"));
                }
            }
        }
    }

    private S04_TableFunction() {}
}
