package org.example.duckdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.function.BiFunction;

import org.duckdb.DuckDBFunctions;

/**
 * Sample 2: a scalar UDF with a fixed number of arguments.
 *
 * For a fixed arity, declare the argument types with withParameter() (one at
 * a time) or withParameters() (all at once) and supply the implementation
 * with withFunction(). The builder has withFunction() overloads for
 * Supplier (0 args), Function (1 arg), and BiFunction (2 args), so the lambda
 * is cast to the intended functional interface to pick the right one.
 *
 * When the parameter/return types have a direct Java mapping (see the README
 * type-mapping table), they can be declared with a Java Class instead of an
 * explicit DuckDBColumnType -- here Integer.class maps to INTEGER.
 *
 * Object-argument callbacks receive null for SQL NULL inputs, so the guard
 * below keeps java_add(NULL, x) from throwing a NullPointerException.
 */
public final class S02_ScalarFixedArgs {

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {

            BiFunction<Integer, Integer, Integer> add =
                    (a, b) -> (a == null || b == null) ? null : a + b;

            DuckDBFunctions.scalarFunction()
                    .withName("java_add")
                    .withParameters(Integer.class, Integer.class)
                    .withReturnType(Integer.class)
                    .withFunction(add)
                    .register(conn);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT java_add(40, 2) AS sum, java_add(NULL, 7) AS with_null")) {
                rs.next();
                System.out.println("java_add(40, 2)   = " + rs.getInt("sum"));
                int withNull = rs.getInt("with_null");
                System.out.println("java_add(NULL, 7) = " + (rs.wasNull() ? "NULL" : withNull));
            }
        }
    }

    private S02_ScalarFixedArgs() {}
}
