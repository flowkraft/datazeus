SELECT "CustomerID", count(*) AS "OrderCount"
FROM "Orders"
GROUP BY "CustomerID"
ORDER BY "OrderCount" DESC, "CustomerID"
LIMIT 5;
