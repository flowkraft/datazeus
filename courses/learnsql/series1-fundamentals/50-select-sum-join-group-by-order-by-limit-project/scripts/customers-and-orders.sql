SELECT count(*) AS "Rows"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID";
