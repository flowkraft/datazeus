SELECT count(*) AS "Orders",
       sum("Freight") AS "Freight",
       COALESCE(sum("Freight"), 0) AS "Freight, filled"
FROM "Orders"
WHERE "ShipCountry" = 'Japan';
