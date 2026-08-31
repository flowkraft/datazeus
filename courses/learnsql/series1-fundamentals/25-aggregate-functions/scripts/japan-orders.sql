SELECT count(*) AS "Orders",
       sum("Freight") AS "Total freight"
FROM "Orders"
WHERE "ShipCountry" = 'Japan';
