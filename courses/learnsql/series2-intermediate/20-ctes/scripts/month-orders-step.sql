WITH month_orders AS (
  SELECT "OrderID", "CustomerID", "Freight"
  FROM "Orders"
  WHERE "OrderDate" >= DATE '2024-05-01'
    AND "OrderDate" <  DATE '2024-06-01'
)
SELECT *
FROM month_orders
WHERE "CustomerID" = 'NORDI'
ORDER BY "OrderID";
