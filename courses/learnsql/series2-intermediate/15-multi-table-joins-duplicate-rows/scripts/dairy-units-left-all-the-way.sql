SELECT p."ProductName",
       sum(d."Quantity") AS "May units"
FROM "Products" p
LEFT JOIN "Order Details" d
  ON d."ProductID" = p."ProductID"
LEFT JOIN "Orders" o
  ON o."OrderID" = d."OrderID"
 AND o."OrderDate" >= DATE '2024-05-01'
 AND o."OrderDate" <  DATE '2024-06-01'
WHERE p."CategoryID" = 4
GROUP BY p."ProductName"
ORDER BY p."ProductName";
