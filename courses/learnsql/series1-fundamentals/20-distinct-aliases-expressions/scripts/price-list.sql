SELECT "ProductName" AS "Product",
       ROUND("UnitPrice", 2) AS "List price",
       ROUND("UnitPrice" * 0.85, 2) AS "Trade price"
FROM "Products"
WHERE "Discontinued" = false
ORDER BY "UnitPrice" DESC
LIMIT 5;
