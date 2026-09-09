package com.example.recon;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "report", description = "Write exceptions to a file")
class Report implements Callable<Integer> {

    @Option(names = "--date", required = true) String date;
    @Option(names = "--drops", defaultValue = "drops") Path drops;
    @Option(names = { "-f", "--format" }, defaultValue = "csv") String format;
    @Option(names = { "-o", "--out" }) Path out;

    @Override
    public Integer call() throws Exception {
        Path target = out != null ? out : Path.of("exceptions_" + date + "." + format);
        String union = "SELECT 'unpaid' AS kind, order_id, NULL AS diff FROM (" + Check.load("unpaid") + ")"
                     + " UNION ALL "
                     + "SELECT 'drift', order_id, diff FROM (" + Check.load("drift") + ")";
        try (Connection c = Db.open(drops.resolve(date));
             Statement s = c.createStatement()) {
            if (format.equals("xlsx")) {
                s.execute("INSTALL excel");
                s.execute("LOAD excel");
            }
            s.execute("COPY (" + union + ") TO '" + target + "' (FORMAT " + format + ")");
        }
        System.out.println("wrote " + target);
        return 0;
    }
}
