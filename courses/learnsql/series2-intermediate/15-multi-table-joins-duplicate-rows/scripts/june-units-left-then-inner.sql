SELECT p."ProductName",
       sum(d."Quantity") AS "June units"
FROM "Products" p
LEFT JOIN "Order Details" d
  ON d."ProductID" = p."ProductID"
JOIN "Orders" o
  ON o."OrderID" = d."OrderID"
 AND o."OrderDate" >= DATE '2024-06-01'
 AND o."OrderDate" <  DATE '2024-07-01'
GROUP BY p."ProductName"
ORDER BY p."ProductName";
