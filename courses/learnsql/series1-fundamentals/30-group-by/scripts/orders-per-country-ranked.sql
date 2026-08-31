SELECT "ShipCountry", count(*) AS "OrderCount"
FROM "Orders"
GROUP BY "ShipCountry"
ORDER BY "OrderCount" DESC, "ShipCountry"
LIMIT 5;
