SELECT date_trunc('month', "OrderDate") AS "Month",
       count(*) AS "Orders"
FROM "Orders"
WHERE "CustomerID" = 'BRAM3'
  AND "OrderDate" >= DATE '2024-01-01'
  AND "OrderDate" <  DATE '2025-01-01'
GROUP BY date_trunc('month', "OrderDate")
ORDER BY "Month";
