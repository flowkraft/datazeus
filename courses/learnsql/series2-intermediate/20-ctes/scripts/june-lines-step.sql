WITH june_lines AS (
  SELECT d."OrderID", d."ProductID",
         d."Quantity"
  FROM "Order Details" d
  JOIN "Orders" o
    ON o."OrderID" = d."OrderID"
  WHERE o."OrderDate" >= '2024-06-01'
    AND o."OrderDate" <  '2024-07-01'
)
SELECT *
FROM june_lines
ORDER BY "OrderID", "ProductID";
