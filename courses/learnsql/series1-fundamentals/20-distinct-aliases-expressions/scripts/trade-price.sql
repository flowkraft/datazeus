SELECT "ProductName" AS "Product",
       ROUND("UnitPrice" * 0.85, 2) AS "Trade price"
FROM "Products"
ORDER BY "UnitPrice" DESC
LIMIT 5;
