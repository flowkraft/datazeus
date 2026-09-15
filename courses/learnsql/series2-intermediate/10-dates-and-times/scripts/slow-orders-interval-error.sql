SELECT count(*) AS "Slow orders"
FROM "Orders"
WHERE "ShippedDate" - "OrderDate" > 7;
