SELECT c."CompanyName"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
JOIN "Order Details" d
  ON d."OrderID" = o."OrderID"
WHERE d."ProductID" = 1
ORDER BY c."CompanyName";
