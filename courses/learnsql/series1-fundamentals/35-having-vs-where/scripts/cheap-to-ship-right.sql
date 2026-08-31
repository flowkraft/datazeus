SELECT "ShipCountry", max("Freight") AS "Dearest"
FROM "Orders"
GROUP BY "ShipCountry"
HAVING max("Freight") <= 50
ORDER BY "ShipCountry";
