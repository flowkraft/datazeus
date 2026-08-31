SELECT c."CompanyName", o."OrderID", o."OrderDate"
FROM "Orders" o
JOIN "Customers" c
  ON c."CustomerID" = o."CustomerID"
WHERE o."ShippedDate" IS NULL
ORDER BY o."OrderDate"
LIMIT 5;
