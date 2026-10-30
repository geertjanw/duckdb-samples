package org.example;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DuckDB and Python in one JVM process. DuckDB (a Java library, via JDBC) runs
 * the vectorized SQL over the full dataset; Python (on GraalPy) applies the
 * business rules to the small aggregated result. No REST, no subprocess, no
 * serialization between them -- the division the GraalPy + Micronaut talk is
 * about, with DuckDB as the Java-side library.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Object[][] rows = aggregateWithDuckDb();

        String python = new String(
            Main.class.getResourceAsStream("/report.py").readAllBytes(),
            StandardCharsets.UTF_8);

        // allowAllAccess lets the embedded Python read the Java arrays we pass in.
        try (Context context = Context.newBuilder("python").allowAllAccess(true).build()) {
            context.eval("python", python);
            Value buildReport = context.getBindings("python").getMember("build_report");
            Value report = buildReport.execute((Object) rows);
            System.out.println(report.asString());
        }
    }

    /** DuckDB generates 1,000 sales rows and aggregates them by region. */
    private static Object[][] aggregateWithDuckDb() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             Statement stmt = conn.createStatement()) {

            stmt.execute("SELECT setseed(0.42)");
            stmt.execute("""
                CREATE TABLE sales AS
                SELECT ['North','South','East','West'][1 + floor(random() * 4)::INT] AS region,
                       ['Coffee','Tea','Juice'][1 + floor(random() * 3)::INT]        AS product,
                       round(5 + random() * 95, 2)                                   AS amount
                FROM range(1000)
                """);

            List<Object[]> out = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("""
                    SELECT region,
                           count(*)              AS orders,
                           round(sum(amount), 2) AS revenue,
                           round(avg(amount), 2) AS avg_order
                    FROM sales
                    GROUP BY region
                    ORDER BY revenue DESC
                    """)) {
                while (rs.next()) {
                    out.add(new Object[]{
                        rs.getString("region"),
                        rs.getLong("orders"),
                        rs.getDouble("revenue"),
                        rs.getDouble("avg_order")
                    });
                }
            }
            return out.toArray(new Object[0][]);
        }
    }
}
