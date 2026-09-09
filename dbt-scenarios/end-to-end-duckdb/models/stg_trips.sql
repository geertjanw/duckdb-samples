-- DEVELOP. The "source" is just a Parquet file — a URL here, a local glob if you
-- point the `trips` var at one. DuckDB reads it directly; no load step, no
-- external tables. Materialized as a table so the remote read happens once and
-- everything downstream is local and instant.
{{ config(materialized='table') }}

select
    tpep_pickup_datetime                         as pickup_at,
    tpep_dropoff_datetime                        as dropoff_at,
    passenger_count,
    trip_distance,
    payment_type,
    fare_amount,
    tip_amount,
    total_amount,
    extract(hour from tpep_pickup_datetime)::int as pickup_hour
from read_parquet('{{ var("trips") }}')
where fare_amount > 0
  and trip_distance >= 0
