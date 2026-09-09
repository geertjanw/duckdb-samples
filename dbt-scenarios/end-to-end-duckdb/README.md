# end-to-end-duckdb

A dbt project **developed, tested, deployed, and served entirely on DuckDB** —
no server, no account, no warehouse. Built to demo live: the speaker types a
handful of plain `dbt` commands, and everything runs from scratch on this laptop.

The data is NYC yellow-taxi trips (~3M rows for one month). DuckDB reads the
Parquet **straight from the public URL** — there's nothing to download or set up.

## The four commands (that's the whole demo)

```bash
cd dbt-scenarios/end-to-end-duckdb
export DBT_PROFILES_DIR=.

dbt run                 # DEVELOP  — reads the Parquet, builds 3 models + a Python model
dbt test                # TEST     — schema tests + "credit-card riders tip, cash riders don't"
                        # DEPLOY   — dbt run already wrote web/*.parquet (open format)
cd web && python3 -m http.server 8000     # SERVE — open http://localhost:8000 (DuckDB-WASM)
```

`dbt run` builds everything, including the two `publish_*` models that write the
results out as **open Parquet** into `web/`. The browser page then queries those
files with **DuckDB-WASM** — no backend at all.

## What's in it

| File | Role |
|---|---|
| `models/stg_trips.sql` | DEVELOP — reads Parquet from a URL (the `trips` var), materialized as a table |
| `models/payment_summary.sql` | mart: tips by payment method |
| `models/trips_by_hour.py` | **Python model**, run in-process — trips by hour of day |
| `models/publish_*.sql` | DEPLOY — `materialized: external`, writes `web/*.parquet` |
| `models/schema.yml` + `tests/assert_card_tips_beat_cash.sql` | TEST |
| `web/index.html` | SERVE — DuckDB-WASM queries the published Parquet in the browser |

## More data than you'd think reasonable

Point the `trips` var at a local folder of Parquet to run on far more — a whole
year is ~40M rows and still builds in seconds, because the scan isn't the
bottleneck. For a stage demo, pre-download so nothing depends on the Wi-Fi:

```bash
mkdir -p data
duckdb -c "COPY (SELECT * FROM read_parquet('https://d37ci6vzurychx.cloudfront.net/trip-data/yellow_tripdata_2024-01.parquet')) TO 'data/2024-01.parquet'"
dbt run  --vars '{trips: "data/*.parquet"}'
dbt test --vars '{trips: "data/*.parquet"}'
```

## Notes

- **`dbt-duckdb`** provides everything: `pip install dbt-duckdb pandas` (pandas is
  only for the Python model). Verified on dbt-core 1.10 / dbt-duckdb 1.10.
- **DuckLake** is the production version of the "deploy" step — publishing tables
  into a managed catalog of open Parquet. This sample uses a plain `external`
  Parquet file, which is all the browser needs and keeps the demo to one moving part.
- **Native readers** — `stg_trips` reads Parquet in place, but the same "read the
  source where it lives, no load step" pattern extends to other formats: swap
  `read_parquet(...)` for `iceberg_scan('...')` (Iceberg tables) or
  `postgres_scan('...', 'schema', 'table')` (a live Postgres) and the rest of the
  project is unchanged. Kept out of the live demo to avoid standing up extra systems.
