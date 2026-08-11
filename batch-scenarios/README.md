# Batch scenarios

Two Spring Boot + **Spring Batch** applications that run the *same* data
transformation over the *same* generated dataset, one built the traditional way
and one handing the work to DuckDB's vectorized engine:

| Demo | Transform step does… |
|---|---|
| [`spring-batch-java-demo`](spring-batch-java-demo) | Loops over rows one at a time and folds them into an in-memory Java `HashMap` of accumulators |
| [`spring-batch-duckdb-demo`](spring-batch-duckdb-demo) | Hands the whole aggregation to DuckDB as a single SQL statement |

This illustrates the use case: *data transformation steps inside batch jobs,
where DuckDB's vectorized engine is dramatically faster than looping over JDBC
rows or using in-memory Java collections.*

## The transformation

Both apps generate a deterministic `orders.csv` (`id, customer_id, category,
quantity, amount`) and compute, per `(customer_id, category)` group:

- `order_count` — number of orders
- `total_revenue` — `sum(amount * quantity)`
- `total_quantity` — `sum(quantity)`
- `avg_amount` — `avg(amount)`
- `max_revenue` — `max(amount * quantity)`

The generator has no randomness, so both apps produce **byte-identical input**
and their summary outputs match exactly (verified with `cmp`). The only thing
that differs is the engine doing the transform.

In `spring-batch-java-demo` the transform is a `Tasklet` that reads the CSV with
a `BufferedReader` and accumulates into a `HashMap<Long, Acc>` — the classic
row-at-a-time, in-memory-collections style (reading the same rows over JDBC with
a `JdbcCursorItemReader` costs the same; the expense is the per-row Java work,
not where the rows come from). In `spring-batch-duckdb-demo` the transform is a
`Tasklet` that runs one `COPY (SELECT … GROUP BY …) TO …` statement; DuckDB
reads, groups, and writes in a single vectorized pass across all cores.

## Results

Measured on this machine (Apple Silicon, 12 threads, Java 21, DuckDB 1.5.5),
timing only the transform step — CSV generation is a one-time setup cost and is
excluded. Fresh JVM per run, so the Java figure includes JIT warmup, which is
realistic for a batch job that starts, runs once, and exits.

| Rows | Java (in-memory collections) | DuckDB (vectorized) | Speedup |
|---|---|---|---|
| 10,000,000 | 1.00 s | 0.17 s | ~6× |
| 50,000,000 | 4.83 s | 0.61 s | ~8× |

The Java loop is single-threaded and pays per-row parsing and boxing costs;
DuckDB vectorizes the scan and aggregation and uses every core by default — which
is precisely why you would reach for it inside a batch job. The gap widens with
scale, and would widen further with a heavier per-row transform. Both produce
the identical 8,000-group summary.

## Running

Each app is a standalone Spring Boot batch job. Run it from its own directory so
the shared `../data` folder resolves to `batch-scenarios/data`:

```bash
cd spring-batch-duckdb-demo
mvn spring-boot:run          # generates ../data/orders.csv on first run, then transforms

cd ../spring-batch-java-demo
mvn spring-boot:run          # reuses the same ../data/orders.csv
```

Change the dataset size with `--app.rows`, e.g.:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--app.rows=50000000
# or against a built jar:
java -jar target/spring-batch-duckdb-demo-0.0.1-SNAPSHOT.jar --app.rows=50000000
```

The generated `orders.csv` and the two `summary-*.csv` files land in
`batch-scenarios/data/` (git-ignored). Delete that folder to regenerate.
