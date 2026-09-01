SELECT "ProductName" AS "Product",
       ROUND("UnitPrice" * 0.9137, 2) AS "Price in EUR"
FROM "Products"
ORDER BY "UnitPrice" DESC
LIMIT 5;
