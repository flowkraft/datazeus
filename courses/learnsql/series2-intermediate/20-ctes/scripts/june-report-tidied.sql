WITH order_lines AS (
  SELECT d."ProductID", d."Quantity",
         o."OrderDate"
  FROM "Order Details" d
  JOIN "Orders" o
    ON o."OrderID" = d."OrderID"
)
SELECT p."ProductName",
       sum(l."Quantity") AS "June units"
FROM "Products" p
LEFT JOIN order_lines l
  ON l."ProductID" = p."ProductID"
WHERE l."OrderDate" >= '2024-06-01'
  AND l."OrderDate" <  '2024-07-01'
GROUP BY p."ProductName"
ORDER BY p."ProductName";
