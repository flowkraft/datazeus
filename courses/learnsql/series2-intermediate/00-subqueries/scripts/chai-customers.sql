SELECT "CompanyName"
FROM "Customers"
WHERE "CustomerID" IN (
  SELECT o."CustomerID"
  FROM "Orders" o
  JOIN "Order Details" d
    ON d."OrderID" = o."OrderID"
  WHERE d."ProductID" = 1
)
ORDER BY "CompanyName";
