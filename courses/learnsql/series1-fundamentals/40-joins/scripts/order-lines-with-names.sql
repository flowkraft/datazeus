SELECT p."ProductName", d."Quantity", d."UnitPrice"
FROM "Order Details" d
JOIN "Products" p
  ON p."ProductID" = d."ProductID"
WHERE d."OrderID" = 3
ORDER BY p."ProductName";
