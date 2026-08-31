SELECT "CustomerID", sum("Freight") AS "TotalFreight"
FROM "Orders"
GROUP BY "CustomerID"
ORDER BY "TotalFreight" DESC
LIMIT 5;
