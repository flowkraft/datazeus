SELECT "ShipCountry", "OrderID", "Freight"
FROM "Orders"
WHERE "ShipCountry" = 'Germany'
  AND "Freight" > 50
ORDER BY "Freight" DESC
LIMIT 6;
