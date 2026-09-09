-- A normal dbt model: reads staging via ref() and aggregates. The punchline —
-- credit-card riders tip ~29%, while cash tips get recorded as $0.
select
    payment_type,
    case payment_type
        when 1 then 'Credit card'
        when 2 then 'Cash'
        when 3 then 'No charge'
        when 4 then 'Dispute'
        when 5 then 'Unknown'
        when 6 then 'Voided'
        else 'Other'
    end                                                    as payment_method,
    count(*)                                               as trips,
    round(avg(fare_amount), 2)                             as avg_fare,
    round(avg(tip_amount), 2)                              as avg_tip,
    round(100.0 * avg(tip_amount / nullif(fare_amount, 0)), 1) as tip_pct
from {{ ref('stg_trips') }}
group by payment_type
order by trips desc
