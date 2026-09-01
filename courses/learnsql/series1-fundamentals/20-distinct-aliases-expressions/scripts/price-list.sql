SELECT "ProductName" AS "Product",
       "UnitPrice" AS "List price",
       ROUND("UnitPrice" * 0.9137, 2) AS "Price in EUR"
FROM "Products"
WHERE "Discontinued" = false
ORDER BY "UnitPrice" DESC
LIMIT 5;
