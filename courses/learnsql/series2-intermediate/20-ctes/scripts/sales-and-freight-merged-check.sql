WITH month_orders AS (
  SELECT "OrderID", "CustomerID", "Freight"
  FROM "Orders"
  WHERE "OrderDate" >= DATE '2024-05-01'
    AND "OrderDate" <  DATE '2024-06-01'
),
totals AS (
  SELECT o."CustomerID",
         ROUND(SUM(d."UnitPrice" * d."Quantity"
           * (1 - d."Discount")), 2) AS "Total sales",
         SUM(o."Freight") AS "Freight"
  FROM month_orders o
  JOIN "Order Details" d ON d."OrderID" = o."OrderID"
  GROUP BY o."CustomerID"
),
report AS (
  SELECT c."CompanyName", t."Total sales", t."Freight"
  FROM "Customers" c
  JOIN totals t ON t."CustomerID" = c."CustomerID"
)
SELECT count(*) AS "Rows",
       sum("Total sales") AS "Sales",
       sum("Freight") AS "Freight"
FROM report;
