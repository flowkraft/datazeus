SELECT c."CompanyName",
       ROUND(SUM(d."UnitPrice" * d."Quantity"
         * (1 - d."Discount")), 2) AS "Total sales",
       SUM(o."Freight") AS "Freight"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
JOIN "Order Details" d
  ON d."OrderID" = o."OrderID"
GROUP BY c."CompanyName"
ORDER BY "Freight" DESC
LIMIT 5;
