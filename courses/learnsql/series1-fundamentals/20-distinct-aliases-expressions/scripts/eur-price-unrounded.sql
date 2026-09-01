SELECT "ProductName" AS "Product",
       "UnitPrice" * 0.9137 AS "Price in EUR"
FROM "Products"
ORDER BY "UnitPrice" DESC
LIMIT 5;
