SELECT "ShipCountry", "ShipCity", count(*) AS "OrderCount"
FROM "Orders"
GROUP BY "ShipCountry", "ShipCity"
ORDER BY "ShipCountry", "ShipCity", "OrderCount" DESC;
