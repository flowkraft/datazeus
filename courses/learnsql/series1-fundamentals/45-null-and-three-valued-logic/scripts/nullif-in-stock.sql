SELECT "ProductName", NULLIF("UnitsInStock", 0) AS "InStock"
FROM "Products"
ORDER BY "UnitsInStock", "ProductName"
LIMIT 3;
