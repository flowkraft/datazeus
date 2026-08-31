SELECT "ShipCountry", "ShipCity", count(*) AS "OrderCount"
FROM "Orders"
GROUP BY "ShipCountry", "ShipCity"
ORDER BY "OrderCount" DESC, "ShipCountry"
LIMIT 5;
