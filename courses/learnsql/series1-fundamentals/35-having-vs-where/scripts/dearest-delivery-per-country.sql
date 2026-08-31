SELECT "ShipCountry", max("Freight") AS "Dearest"
FROM "Orders"
GROUP BY "ShipCountry"
ORDER BY "Dearest" DESC
LIMIT 5;
