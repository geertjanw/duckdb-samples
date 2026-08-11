package com.example.batchjava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The "traditional" transformation step: read every order row over an
 * {@link BufferedReader}, parse it into fields, and fold it into an in-memory
 * {@link HashMap} of accumulators — one bucket per (customer_id, category). This
 * is the row-at-a-time, in-memory-Java-collections style the DuckDB demo is
 * contrasted against. Only the transform is timed; generating the input CSV is
 * a one-time setup cost and is excluded.
 */
class JavaTransformTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(JavaTransformTasklet.class);

    private final Path ordersCsv;
    private final Path summaryCsv;
    private final long rows;

    JavaTransformTasklet(Path ordersCsv, Path summaryCsv, long rows) {
        this.ordersCsv = ordersCsv;
        this.summaryCsv = summaryCsv;
        this.rows = rows;
    }

    /** Mutable accumulator for one (customer_id, category) group. */
    private static final class Acc {
        long count;
        double totalRevenue;
        long totalQuantity;
        double sumAmount;
        double maxRevenue;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        long start = System.nanoTime();

        Map<Long, Acc> groups = new HashMap<>();
        long parsed = 0;
        try (BufferedReader r = Files.newBufferedReader(ordersCsv)) {
            r.readLine(); // header
            String line;
            while ((line = r.readLine()) != null) {
                // id,customer_id,category,quantity,amount
                int c1 = line.indexOf(',');
                int c2 = line.indexOf(',', c1 + 1);
                int c3 = line.indexOf(',', c2 + 1);
                int c4 = line.indexOf(',', c3 + 1);

                long customerId = Long.parseLong(line, c1 + 1, c2, 10);
                long category = Long.parseLong(line, c2 + 1, c3, 10);
                long quantity = Long.parseLong(line, c3 + 1, c4, 10);
                double amount = Double.parseDouble(line.substring(c4 + 1));

                double revenue = amount * quantity;
                Long key = customerId * 8 + category;
                Acc acc = groups.computeIfAbsent(key, k -> new Acc());
                acc.count++;
                acc.totalRevenue += revenue;
                acc.totalQuantity += quantity;
                acc.sumAmount += amount;
                if (revenue > acc.maxRevenue) {
                    acc.maxRevenue = revenue;
                }
                parsed++;
            }
        }

        // Emit the aggregated summary, sorted by (customer_id, category) so it
        // lines up with the DuckDB demo's ORDER BY.
        Files.createDirectories(summaryCsv.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(summaryCsv)) {
            w.write("customer_id,category,order_count,total_revenue,total_quantity,avg_amount,max_revenue\n");
            groups.entrySet().stream()
                    .sorted(Comparator.comparingLong(Map.Entry::getKey))
                    .forEach(e -> writeRow(w, e.getKey(), e.getValue()));
        }

        double seconds = (System.nanoTime() - start) / 1e9;
        log.info("Java in-memory transform: {} rows -> {} groups in {} s ({} rows/s)",
                parsed, groups.size(), String.format(Locale.US, "%.2f", seconds),
                String.format(Locale.US, "%,.0f", parsed / seconds));
        System.out.printf(Locale.US,
                "%n[java] transformed %,d rows into %,d groups in %.2f s (%,.0f rows/s)%n",
                parsed, groups.size(), seconds, parsed / seconds);
        return RepeatStatus.FINISHED;
    }

    private static void writeRow(BufferedWriter w, long key, Acc a) {
        long customerId = key / 8;
        long category = key % 8;
        double avgAmount = a.sumAmount / a.count;
        try {
            w.write(String.format(Locale.US, "%d,%d,%d,%.2f,%d,%.2f,%.2f\n",
                    customerId, category, a.count, a.totalRevenue, a.totalQuantity, avgAmount, a.maxRevenue));
        } catch (java.io.IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
