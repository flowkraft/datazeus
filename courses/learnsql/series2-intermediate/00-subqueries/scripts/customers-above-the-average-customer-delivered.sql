SELECT c."CompanyName",
       ROUND(SUM(d."UnitPrice" * d."Quantity"
         * (1 - d."Discount")), 2) AS "Delivered sales"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
JOIN "Order Details" d
  ON d."OrderID" = o."OrderID"
WHERE o."ShippedDate" IS NOT NULL
GROUP BY c."CompanyName"
HAVING SUM(d."UnitPrice" * d."Quantity"
         * (1 - d."Discount"))
     > (SELECT AVG(t."Total")
        FROM (SELECT o2."CustomerID",
                     SUM(d2."UnitPrice"
                       * d2."Quantity"
                       * (1 - d2."Discount")) AS "Total"
              FROM "Orders" o2
              JOIN "Order Details" d2
                ON d2."OrderID" = o2."OrderID"
              WHERE o2."ShippedDate" IS NOT NULL
              GROUP BY o2."CustomerID") AS t)
ORDER BY "Delivered sales" DESC;
