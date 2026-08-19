package org.example.duckdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.duckdb.DuckDBColumnType;
import org.duckdb.DuckDBFunctions;
import org.duckdb.DuckDBLogicalType;

/**
 * Sample 1: a scalar UDF with a variable number of arguments.
 *
 * A scalar function is built with the DuckDBFunctions.scalarFunction()
 * builder and registered on a connection with register(). After that it can
 * be called from SQL by the name passed to withName().
 *
 * For a variadic argument list, declare the element type with withVarArgs()
 * and supply the implementation with withVarArgsFunction(). The callback
 * receives all arguments as an Object[]; SQL NULL inputs arrive as null.
 *
 * A DuckDBLogicalType is a native resource, so it is created in a
 * try-with-resources block and closed once the function is registered.
 */
public final class S01_ScalarVarArgs {

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             DuckDBLogicalType intType = DuckDBLogicalType.of(DuckDBColumnType.INTEGER)) {

            DuckDBFunctions.scalarFunction()
                    .withName("java_sum_varargs")
                    .withParameter(Integer.class) // one fixed leading argument
                    .withVarArgs(intType)          // followed by variadic INTEGERs
                    .withReturnType(Integer.class)
                    .withVarArgsFunction(fnArgs -> {
                        int sum = 0;
                        for (Object arg : fnArgs) {
                            // SQL NULL shows up as a Java null -- skip it.
                            if (arg != null) {
                                sum += (Integer) arg;
                            }
                        }
                        return sum;
                    })
                    .register(conn);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT java_sum_varargs(1, 2, 3, 4) AS total")) {
                rs.next();
                System.out.println("java_sum_varargs(1, 2, 3, 4) = " + rs.getInt("total"));
            }
        }
    }

    private S01_ScalarVarArgs() {}
}
