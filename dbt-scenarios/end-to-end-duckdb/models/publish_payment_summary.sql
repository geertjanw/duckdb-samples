-- DEPLOY. The second published table, written as open Parquet for the browser.
{{ config(materialized='external', location='web/payment_summary.parquet') }}

select * from {{ ref('payment_summary') }}
