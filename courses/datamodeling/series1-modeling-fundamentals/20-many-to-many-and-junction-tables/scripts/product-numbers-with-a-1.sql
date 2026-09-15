SELECT count(*) AS "Product numbers with a 1"
FROM "Products"
WHERE CAST("ProductID" AS VARCHAR)
      LIKE '%1%';
