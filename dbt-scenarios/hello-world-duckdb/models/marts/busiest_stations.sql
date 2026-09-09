-- A normal dbt model: references the staging model with ref() and lets dbt
-- wire the DAG. The DuckDB-specific part (reading Parquet) stays in staging.
select
    station_name,
    train_type,
    count(*)                          as stops
from {{ ref('stg_train_services') }}
group by all
order by stops desc
