SELECT count(*) AS "Orders"
FROM "Orders"
WHERE "ShippedDate" < DATE '2024-01-01'
   OR "ShippedDate" IS NULL;
