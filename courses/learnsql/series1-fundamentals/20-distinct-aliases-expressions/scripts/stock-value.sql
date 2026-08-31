SELECT "ProductName" AS "Product",
       "UnitPrice" * "UnitsInStock" AS "Stock value"
FROM "Products"
ORDER BY "Stock value" DESC
LIMIT 5;
