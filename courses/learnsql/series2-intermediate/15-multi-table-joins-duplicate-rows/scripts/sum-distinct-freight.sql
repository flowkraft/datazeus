SELECT SUM(DISTINCT o."Freight")
         AS "SUM(DISTINCT)"
FROM "Orders" o
JOIN "Order Details" d
  ON d."OrderID" = o."OrderID";
