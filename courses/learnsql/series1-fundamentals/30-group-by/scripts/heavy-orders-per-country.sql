SELECT "ShipCountry", count(*) AS "OrderCount"
FROM "Orders"
WHERE "Freight" > 50
GROUP BY "ShipCountry"
ORDER BY "OrderCount" DESC, "ShipCountry";
