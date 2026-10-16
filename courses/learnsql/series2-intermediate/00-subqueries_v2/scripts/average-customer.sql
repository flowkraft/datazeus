SELECT ROUND(AVG("Total"), 2) AS "Average customer"
FROM (SELECT o."CustomerID",
             SUM(d."UnitPrice" * d."Quantity"
               * (1 - d."Discount"))
               AS "Total"
      FROM "Orders" o
      JOIN "Order Details" d
        ON d."OrderID" = o."OrderID"
      GROUP BY o."CustomerID") AS t;
