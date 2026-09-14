SELECT c."CompanyName",
       count(o."OrderID") AS "Orders",
       ROUND(SUM(d."UnitPrice" * d."Quantity"
         * (1 - d."Discount")), 2) AS "Total sales"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
JOIN "Order Details" d
  ON d."OrderID" = o."OrderID"
GROUP BY c."CompanyName"
ORDER BY "Total sales" DESC;
