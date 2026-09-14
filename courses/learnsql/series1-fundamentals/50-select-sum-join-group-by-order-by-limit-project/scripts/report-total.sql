SELECT ROUND(SUM(d."UnitPrice" * d."Quantity"
         * (1 - d."Discount")), 2) AS "Total"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
JOIN "Order Details" d
  ON d."OrderID" = o."OrderID";
