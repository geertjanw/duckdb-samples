-- DuckDB reads the remote Parquet directly over HTTP range requests;
-- nothing is downloaded up front. This is the DuckDB integration point:
-- the "source" is just a file URL, no load step, no external table setup.
select
    service_id,
    date              as service_date,
    type              as train_type,
    train_number,
    station_code,
    station_name,
    departure_time,
    arrival_time
from 'https://blobs.duckdb.org/train_services.parquet'
