SELECT count(*) AS "Unshipped",
       count("ShipVia") AS "With a shipper"
FROM "Orders"
WHERE "ShippedDate" IS NULL;
