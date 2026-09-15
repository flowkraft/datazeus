SELECT c."CompanyName", s."Total sales", f."Freight"
FROM "Customers" c
JOIN (SELECT o."CustomerID",
             ROUND(SUM(d."UnitPrice" * d."Quantity"
               * (1 - d."Discount")), 2) AS "Total sales"
      FROM "Orders" o
      JOIN "Order Details" d
        ON d."OrderID" = o."OrderID"
      GROUP BY o."CustomerID") AS s
  ON s."CustomerID" = c."CustomerID"
JOIN (SELECT "CustomerID",
             SUM("Freight") AS "Freight"
      FROM "Orders"
      GROUP BY "CustomerID") AS f
  ON f."CustomerID" = c."CustomerID"
ORDER BY f."Freight" DESC
LIMIT 5;
