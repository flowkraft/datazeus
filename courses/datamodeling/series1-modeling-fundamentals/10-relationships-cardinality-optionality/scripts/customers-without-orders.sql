SELECT count(*) AS "Customers without orders"
FROM "Customers" c
LEFT JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
WHERE o."OrderID" IS NULL;
