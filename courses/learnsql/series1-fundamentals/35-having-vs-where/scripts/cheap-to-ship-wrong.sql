SELECT "ShipCountry", max("Freight") AS "Dearest"
FROM "Orders"
WHERE "Freight" <= 50
GROUP BY "ShipCountry"
ORDER BY "ShipCountry";
