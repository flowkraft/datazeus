SELECT count(*)
FROM "Products"
WHERE NOT ("CategoryID" = 1 OR "CategoryID" = 2);
