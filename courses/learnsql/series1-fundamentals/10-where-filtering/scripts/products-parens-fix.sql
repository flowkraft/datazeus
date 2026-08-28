SELECT "ProductName", "UnitPrice"
FROM "Products"
WHERE ("CategoryID" = 1 OR "CategoryID" = 2)
  AND "UnitPrice" < 15;
