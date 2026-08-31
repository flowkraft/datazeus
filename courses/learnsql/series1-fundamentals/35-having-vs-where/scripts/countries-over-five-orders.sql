SELECT "ShipCountry", count(*) AS "OrderCount"
FROM "Orders"
GROUP BY "ShipCountry"
HAVING count(*) > 5
ORDER BY "OrderCount" DESC, "ShipCountry";
