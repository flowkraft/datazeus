SELECT o."OrderID", o."Freight",
       count(*) AS "Lines"
FROM "Orders" o
JOIN "Order Details" d
  ON d."OrderID" = o."OrderID"
WHERE o."CustomerID" = 'NORDI'
  AND o."OrderDate" >= DATE '2024-05-01'
  AND o."OrderDate" <  DATE '2024-06-01'
GROUP BY o."OrderID", o."Freight"
ORDER BY o."OrderID";
