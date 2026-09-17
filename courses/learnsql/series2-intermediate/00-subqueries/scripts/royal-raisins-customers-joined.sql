SELECT c."CompanyName"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
JOIN "Order Details" d
  ON d."OrderID" = o."OrderID"
WHERE d."ProductID" = 39
  AND o."OrderDate" >= DATE '2024-01-01'
ORDER BY c."CompanyName";
