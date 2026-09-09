SELECT o.order_id, o.total, p.amount, o.total - p.amount AS diff
FROM orders o
JOIN payouts p USING (order_id)
WHERE abs(o.total - p.amount) > 0.01;
