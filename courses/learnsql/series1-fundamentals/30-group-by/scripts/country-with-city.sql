SELECT "ShipCountry", "ShipCity", count(*) AS "OrderCount"
FROM "Orders"
GROUP BY "ShipCountry";
