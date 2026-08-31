SELECT o."OrderID", c."CompanyName", o."OrderDate"
FROM "Orders" o
JOIN "Customers" c
  ON c."CustomerID" = o."CustomerID"
ORDER BY o."OrderDate" DESC
LIMIT 5;
