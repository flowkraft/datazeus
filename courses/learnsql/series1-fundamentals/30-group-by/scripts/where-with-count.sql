SELECT "ShipCountry", count(*) AS "OrderCount"
FROM "Orders"
WHERE count(*) > 5
GROUP BY "ShipCountry";
