SELECT "ShipCountry", count(*) AS "OrderCount"
FROM "Orders"
WHERE "Freight" > 50
GROUP BY "ShipCountry"
HAVING count(*) > 2
ORDER BY "OrderCount" DESC, "ShipCountry";
