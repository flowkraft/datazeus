SELECT count(*) AS "Unshipped"
FROM "Orders"
WHERE "ShippedDate" = NULL;
