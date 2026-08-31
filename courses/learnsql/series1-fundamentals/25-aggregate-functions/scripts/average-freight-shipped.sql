SELECT count(*) AS "Shipped orders",
       avg("Freight") AS "Average freight"
FROM "Orders"
WHERE "ShippedDate" IS NOT NULL;
