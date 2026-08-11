package com.example.batchjava;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Generates a deterministic {@code orders.csv} if it does not already exist.
 *
 * The formula is intentionally free of randomness so that this Java generator
 * and the DuckDB demo's generator produce byte-identical files — that is what
 * lets the two apps' summary outputs be compared directly.
 *
 * Columns: id,customer_id,category,quantity,amount
 */
final class OrdersCsvGenerator {

    private OrdersCsvGenerator() {
    }

    static void generateIfMissing(Path csv, long rows) throws IOException {
        if (Files.exists(csv)) {
            return;
        }
        Files.createDirectories(csv.getParent());
        Path tmp = csv.resolveSibling(csv.getFileName() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp)) {
            w.write("id,customer_id,category,quantity,amount\n");
            StringBuilder sb = new StringBuilder(64);
            for (long i = 0; i < rows; i++) {
                long customerId = i % 1000;
                long category = (i / 1000) % 8; // block index, independent of customer_id
                long quantity = (i % 10) + 1;
                long amountCents = (i * 7) % 1_000_000; // 0.00 .. 9999.99
                sb.setLength(0);
                sb.append(i).append(',')
                  .append(customerId).append(',')
                  .append(category).append(',')
                  .append(quantity).append(',')
                  .append(String.format(Locale.US, "%d.%02d", amountCents / 100, amountCents % 100))
                  .append('\n');
                w.write(sb.toString());
            }
        }
        Files.move(tmp, csv);
    }
}
