-- DEPLOY. dbt-duckdb's "external" materialization writes the model out as an
-- open Parquet file — your published table, in an open format, no warehouse.
-- (DuckLake does this at production scale; a plain file is all the browser needs.)
{{ config(materialized='external', location='web/trips_by_hour.parquet') }}

select * from {{ ref('trips_by_hour') }}
