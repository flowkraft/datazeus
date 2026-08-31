SELECT "ProductName" AS "Product",
       "UnitPrice" * "UnitsInStock" AS "Stock value"
FROM "Products"
WHERE "Stock value" > 1000;
