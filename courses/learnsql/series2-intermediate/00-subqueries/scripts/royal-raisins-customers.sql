SELECT "CompanyName"
FROM "Customers"
WHERE "CustomerID" IN (
  SELECT o."CustomerID"
  FROM "Orders" o
  JOIN "Order Details" d
    ON d."OrderID" = o."OrderID"
  WHERE d."ProductID" = 39
    AND o."OrderDate" >= DATE '2024-01-01'
)
ORDER BY "CompanyName";
