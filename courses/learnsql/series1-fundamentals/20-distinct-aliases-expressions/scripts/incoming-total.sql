SELECT "ProductName" AS "Product",
       "UnitsInStock" + "UnitsOnOrder" AS "Total units"
FROM "Products"
ORDER BY "Total units" DESC
LIMIT 5;
