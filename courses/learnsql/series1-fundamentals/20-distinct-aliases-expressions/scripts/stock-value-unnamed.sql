SELECT "ProductName",
       "UnitPrice" * "UnitsInStock"
FROM "Products"
ORDER BY "UnitPrice" * "UnitsInStock" DESC
LIMIT 5;
