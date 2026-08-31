SELECT "ShipCountry", count(*) AS "OrderCount"
FROM "Orders"
GROUP BY "ShipCountry"
HAVING sum("Freight") > 300
ORDER BY "ShipCountry";
