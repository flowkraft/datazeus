SELECT "ProductName", "UnitPrice"
FROM "Products"
WHERE "UnitPrice" > (SELECT MAX("UnitPrice")
                     FROM "Products"
                     WHERE "CategoryID" = 1)
ORDER BY "UnitPrice" DESC;
