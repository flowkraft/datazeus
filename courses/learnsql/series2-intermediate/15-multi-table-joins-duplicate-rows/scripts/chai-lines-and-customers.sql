SELECT count(*) AS "Lines",
       count(DISTINCT o."CustomerID")
         AS "Customers"
FROM "Order Details" d
JOIN "Orders" o
  ON o."OrderID" = d."OrderID"
WHERE d."ProductID" = 1;
