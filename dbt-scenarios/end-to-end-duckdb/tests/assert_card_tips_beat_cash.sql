-- TEST. A business fact we assert holds: credit-card riders tip far more than
-- cash riders (cash tips go unrecorded). Returns a row only if that breaks.
select 'card tips did not exceed cash tips' as failure
from {{ ref('payment_summary') }}
having max(case when payment_type = 1 then avg_tip end)
     <= max(case when payment_type = 2 then avg_tip end)
