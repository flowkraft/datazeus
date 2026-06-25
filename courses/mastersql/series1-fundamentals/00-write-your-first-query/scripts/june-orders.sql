-- Series 1 · Episode 00 · the hero query.
-- The boss asks: "How many orders did we ship in June?"  You don't scroll — you ask.
SELECT count(*) AS june_orders
FROM "Orders"
WHERE "OrderDate" >= DATE '2024-06-01'
  AND "OrderDate" <  DATE '2024-07-01';
