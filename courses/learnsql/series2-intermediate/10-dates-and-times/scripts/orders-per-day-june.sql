SELECT date_trunc('day', "OrderDate") AS "Day",
       count(*) AS "Orders"
FROM "Orders"
WHERE "OrderDate" >= DATE '2024-06-01'
  AND "OrderDate" <  DATE '2024-07-01'
GROUP BY date_trunc('day', "OrderDate")
ORDER BY "Day";
