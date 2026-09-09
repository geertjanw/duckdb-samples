package com.example.recon;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "check", description = "Run reconciliation checks")
class Check implements Callable<Integer> {

    @Option(names = "--date", required = true) String date;
    @Option(names = "--drops", defaultValue = "drops") Path drops;
    @Option(names = "--max-exceptions", defaultValue = "50") int max;

    @Override
    public Integer call() throws Exception {
        int total = 0;
        String[] names = { "unpaid", "drift" };
        String[] queries = new String[names.length];
        for (int i = 0; i < names.length; i++) queries[i] = load(names[i]);
        try (Connection c = Db.open(drops.resolve(date));
             Statement s = c.createStatement()) {
            for (int i = 0; i < names.length; i++) {
                String name = names[i];
                String sql = queries[i];
                try (ResultSet r = s.executeQuery("SELECT count(*) FROM (" + sql + ")")) {
                    r.next();
                    int n = r.getInt(1);
                    total += n;
                    System.out.printf("%-8s %6d%n", name, n);
                }
            }
        }
        System.out.printf("total    %6d (limit %d)%n", total, max);
        return total > max ? 2 : 0;
    }

    static String load(String name) throws Exception {
        try (var in = Check.class.getResourceAsStream("/sql/" + name + ".sql")) {
            if (in == null) throw new IllegalStateException("missing resource sql/" + name + ".sql");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                .trim().replaceAll(";$", "");
        }
    }
}
