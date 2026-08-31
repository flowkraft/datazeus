SELECT "ProductName" AS "Product",
       "UnitPrice" * "UnitsInStock" AS "Stock value"
FROM "Products"
WHERE "UnitPrice" * "UnitsInStock" > 1000
ORDER BY "Stock value" DESC;
