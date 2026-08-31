SELECT "ProductName" AS "Product",
       "UnitPrice" * 0.85 AS "Trade price"
FROM "Products"
ORDER BY "UnitPrice" DESC
LIMIT 5;
