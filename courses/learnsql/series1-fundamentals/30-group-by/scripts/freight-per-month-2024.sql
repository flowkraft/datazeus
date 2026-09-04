SELECT EXTRACT(MONTH FROM "OrderDate") AS "Month",
       sum("Freight") AS "TotalFreight"
FROM "Orders"
WHERE "OrderDate" >= DATE '2024-01-01'
GROUP BY EXTRACT(MONTH FROM "OrderDate")
ORDER BY "Month";
