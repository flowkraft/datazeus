SELECT o."OrderID", o."Freight",
       count(*) AS "Lines"
FROM "Orders" o
JOIN "Order Details" d
  ON d."OrderID" = o."OrderID"
WHERE o."CustomerID" = 'FOLKO'
GROUP BY o."OrderID", o."Freight"
ORDER BY o."OrderID";
