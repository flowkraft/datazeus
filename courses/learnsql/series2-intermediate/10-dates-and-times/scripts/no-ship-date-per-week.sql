SELECT date_trunc('week', "OrderDate") AS "Week",
       count(*) AS "Orders",
       SUM(CASE WHEN "Status" = 'Shipped'
                 AND "ShippedDate" IS NULL
           THEN 1 ELSE 0 END) AS "No ship date"
FROM "Orders"
WHERE "OrderDate" >= DATE '2024-01-01'
  AND "OrderDate" <  DATE '2025-01-01'
GROUP BY date_trunc('week', "OrderDate")
ORDER BY "Week";
