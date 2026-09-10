SELECT count(*) AS "Rows"
FROM "Customers" c
JOIN "Orders" o ON c."Region" = o."ShipRegion";
