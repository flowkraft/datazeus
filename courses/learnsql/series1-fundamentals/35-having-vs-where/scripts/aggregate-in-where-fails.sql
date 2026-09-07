SELECT "ShipCountry", count(*)
FROM "Orders"
WHERE count(*) > 5
GROUP BY "ShipCountry";
