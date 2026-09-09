# hello-world-duckdb

The same dbt-duckdb setup as [`../hello-world`](../hello-world), but now DuckDB
actually earns its place: a staging model reads a **remote Parquet file directly**
— no ingestion job, no `COPY`, no external-table DDL — and a mart aggregates it
through a normal dbt `ref()`.

The data is DuckDB's public `train_services` dataset (~381,000 Dutch railway
stop records).

## Models

| Model | Materialized | What it does |
|---|---|---|
| `staging/stg_train_services.sql` | view | `select ... from '<parquet URL>'` — DuckDB reads `train_services.parquet` over HTTP range requests |
| `marts/busiest_stations.sql` | table | Number of stops per station and train type, built from `ref('stg_train_services')` |

The integration point is one line: DuckDB treats a file URL as a table, so a dbt
model's `from` clause can point straight at Parquet, CSV, or JSON — local or
remote. `httpfs` (enabled in `profiles.yml`) handles the HTTP reads.

> dbt-duckdb also supports declaring these as dbt **sources** via
> `external_location` in a `sources.yml`, if you prefer `source()` over an inline
> URL. This sample keeps the URL in the model to make the integration obvious.

## Run it

```bash
pip install dbt-duckdb
cd dbt-scenarios/hello-world-duckdb
dbt run --profiles-dir .         # builds stg_train_services + busiest_stations in warehouse.duckdb
dbt show --profiles-dir . --select busiest_stations
```

Expected top rows from `dbt show`:

```
| station_name       | train_type | stops |
| ------------------ | ---------- | ----- |
| Utrecht Centraal   | Intercity  |  3901 |
| Utrecht Centraal   | Sprinter   |  3508 |
| Amsterdam Centraal | Intercity  |  3444 |
```
