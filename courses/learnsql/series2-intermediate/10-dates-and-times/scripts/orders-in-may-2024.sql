SELECT count(*) AS "Orders"
FROM "Orders"
WHERE "OrderDate" >= '2024-05-01'
  AND "OrderDate" <  '2024-06-01';
