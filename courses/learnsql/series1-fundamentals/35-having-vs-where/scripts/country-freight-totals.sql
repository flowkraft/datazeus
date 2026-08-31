SELECT "ShipCountry", sum("Freight") AS "TotalFreight"
FROM "Orders"
GROUP BY "ShipCountry"
ORDER BY "TotalFreight" DESC
LIMIT 5;
