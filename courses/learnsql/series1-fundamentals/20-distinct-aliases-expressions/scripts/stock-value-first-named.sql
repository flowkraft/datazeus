SELECT "ProductName" AS "Product",
       "UnitPrice" * "UnitsInStock"
FROM "Products"
ORDER BY "UnitPrice" * "UnitsInStock" DESC
LIMIT 5;
