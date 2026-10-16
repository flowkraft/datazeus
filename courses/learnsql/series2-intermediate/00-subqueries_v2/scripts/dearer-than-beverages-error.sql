SELECT "ProductName", "UnitPrice"
FROM "Products"
WHERE "UnitPrice" > (SELECT "UnitPrice"
                     FROM "Products"
                     WHERE "CategoryID" = 1);
