package com.example.recon;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "sql", description = "Run an ad hoc query against the day's exports")
class Sql implements Callable<Integer> {

    @Parameters(index = "0", description = "SQL query") String query;
    @Option(names = "--date", required = true) String date;
    @Option(names = "--drops", defaultValue = "drops") Path drops;

    @Override
    public Integer call() throws Exception {
        try (Connection c = Db.open(drops.resolve(date));
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(query)) {
            printTable(r);
        }
        return 0;
    }

    static void printTable(ResultSet r) throws Exception {
        ResultSetMetaData m = r.getMetaData();
        int cols = m.getColumnCount();
        List<String[]> rows = new ArrayList<>();
        String[] header = new String[cols];
        for (int i = 0; i < cols; i++) header[i] = m.getColumnLabel(i + 1);
        rows.add(header);
        while (r.next()) {
            String[] row = new String[cols];
            for (int i = 0; i < cols; i++) row[i] = String.valueOf(r.getObject(i + 1));
            rows.add(row);
        }
        int[] w = new int[cols];
        for (String[] row : rows)
            for (int i = 0; i < cols; i++) w[i] = Math.max(w[i], row[i].length());
        for (String[] row : rows) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < cols; i++) {
                if (i > 0) b.append("  ");
                b.append(String.format("%-" + w[i] + "s", row[i]));
            }
            System.out.println(b);
        }
    }
}
