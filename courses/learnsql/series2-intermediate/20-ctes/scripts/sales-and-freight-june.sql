WITH month_orders AS (
  SELECT "OrderID", "CustomerID", "Freight"
  FROM "Orders"
  WHERE "OrderDate" >= DATE '2024-06-01'
    AND "OrderDate" <  DATE '2024-07-01'
),
sales AS (
  SELECT o."CustomerID",
         ROUND(SUM(d."UnitPrice" * d."Quantity"
           * (1 - d."Discount")), 2) AS "Total sales"
  FROM month_orders o
  JOIN "Order Details" d ON d."OrderID" = o."OrderID"
  GROUP BY o."CustomerID"
),
freight AS (
  SELECT "CustomerID",
         SUM("Freight") AS "Freight"
  FROM month_orders
  GROUP BY "CustomerID"
)
SELECT c."CompanyName", s."Total sales", f."Freight"
FROM "Customers" c
JOIN sales s ON s."CustomerID" = c."CustomerID"
JOIN freight f ON f."CustomerID" = c."CustomerID"
ORDER BY f."Freight" DESC
LIMIT 5;
