SELECT count(*) AS "Shipped orders",
       count("Freight") AS "Freight values",
       avg("Freight") AS "Average freight"
FROM "Orders"
WHERE "ShippedDate" IS NOT NULL;
