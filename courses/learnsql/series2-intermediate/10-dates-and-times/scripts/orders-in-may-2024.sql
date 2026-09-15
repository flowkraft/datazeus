SELECT count(*) AS "Orders"
FROM "Orders"
WHERE "OrderDate" >= DATE '2024-05-01'
  AND "OrderDate" <  DATE '2024-06-01';
