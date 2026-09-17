SELECT c."CompanyName",
       ROUND(SUM(d."UnitPrice" * d."Quantity"
         * (1 - d."Discount")), 2) AS "Total sales",
       SUM(o."Freight") AS "Freight"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
JOIN "Order Details" d
  ON d."OrderID" = o."OrderID"
WHERE o."OrderDate" >= DATE '2024-05-01'
  AND o."OrderDate" <  DATE '2024-06-01'
GROUP BY c."CompanyName"
ORDER BY "Freight" DESC
LIMIT 5;
