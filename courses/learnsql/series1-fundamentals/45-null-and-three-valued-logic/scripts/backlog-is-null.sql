SELECT count(*) AS "Unshipped"
FROM "Orders"
WHERE "ShippedDate" IS NULL;
