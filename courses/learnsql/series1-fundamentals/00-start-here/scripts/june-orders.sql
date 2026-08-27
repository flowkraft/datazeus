SELECT count(*)
FROM "Orders"
WHERE "OrderDate" >= DATE '2024-06-01'
  AND "OrderDate" <  DATE '2024-07-01';
