# recon

A GraalVM native CLI for order-to-cash reconciliation. Finance systems drop
daily export files — orders, payouts, shipments — in mixed formats. `recon`
points DuckDB at those files as views and runs reconciliation queries over them,
then ships as a single native binary that starts instantly, which suits a
per-day cron job or CI step.

The reconciliation logic is plain SQL (`src/main/resources/sql/`), so the checks
are easy to read and change without touching Java.

## How it reads the exports

`Db.java` opens an in-memory DuckDB connection and creates a view per source,
letting DuckDB read each format directly — no import step:

```sql
CREATE VIEW orders    AS SELECT * FROM '<drops>/orders_*.jsonl';
CREATE VIEW payouts   AS SELECT * FROM '<drops>/stripe_*.csv';
CREATE VIEW shipments AS SELECT * FROM '<drops>/ship_*.parquet';
```

## Commands

Built with [picocli](https://picocli.info/); run `recon --help` for the full
list.

| Command | What it does |
|---|---|
| `fetch --date <d>` | Stub for downloading the day's exports (prints the intended action; wire in your own S3 client) |
| `check --date <d>` | Runs each reconciliation query, prints a count per check, exits `2` if the total exceeds `--max-exceptions` (default 50) |
| `report --date <d>` | Writes all exceptions to a file via DuckDB `COPY` — `-f csv` (default), `parquet`, or `xlsx` |
| `sql "<query>" --date <d>` | Runs an ad hoc query against the day's views and prints a table |

Common options: `--drops <dir>` (root of the export folders, default `drops`),
`--date <YYYY-MM-DD>` (which day's subfolder to read).

The two checks:

- **unpaid** — shipments with an order but no matching payout.
- **drift** — orders whose total differs from the payout amount by more than a cent.

## Build and run

```bash
cd graalvm/recon

# build the native binary (target/recon) — the native-maven-plugin
# runs in the package phase, so a plain package builds it
mvn package

# run it against a day's exports
./target/recon check  --date 2026-09-09 --drops drops
./target/recon report --date 2026-09-09 -f xlsx
./target/recon sql "SELECT count(*) FROM orders" --date 2026-09-09
```

To iterate on the JVM without a native build, run the main class
`com.example.recon.Recon` from your IDE or `java -cp ...`.

`target/` is gitignored.
