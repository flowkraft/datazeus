SELECT "ShipCountry", "ShipCity", count(*) AS "OrderCount"
FROM "Orders"
WHERE "ShipCountry" IN ('France', 'Sweden')
GROUP BY "ShipCountry", "ShipCity"
ORDER BY "ShipCountry", "ShipCity";
