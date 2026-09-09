SELECT s.order_id, s.shipped_at, o.total
FROM shipments s
JOIN orders o USING (order_id)
LEFT JOIN payouts p USING (order_id)
WHERE p.order_id IS NULL;
