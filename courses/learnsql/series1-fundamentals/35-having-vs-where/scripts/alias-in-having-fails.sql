SELECT "ShipCountry", count(*) AS "OrderCount"
FROM "Orders"
GROUP BY "ShipCountry"
HAVING "OrderCount" > 5
ORDER BY "ShipCountry";
