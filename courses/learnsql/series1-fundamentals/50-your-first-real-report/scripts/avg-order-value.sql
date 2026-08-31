SELECT c."CompanyName",
       count(DISTINCT o."OrderID") AS "Orders",
       ROUND(SUM(d."UnitPrice" * d."Quantity"
         * (1 - d."Discount")), 2) AS "Total sales",
       ROUND(SUM(d."UnitPrice" * d."Quantity"
         * (1 - d."Discount"))
         / count(DISTINCT o."OrderID"), 2) AS "Avg order"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
JOIN "Order Details" d
  ON d."OrderID" = o."OrderID"
GROUP BY c."CompanyName"
ORDER BY "Total sales" DESC;
