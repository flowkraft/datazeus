WITH order_lines AS (
  SELECT d."ProductID", d."Quantity",
         o."OrderDate"
  FROM "Order Details" d
  JOIN "Orders" o
    ON o."OrderID" = d."OrderID"
),
report AS (
  SELECT p."ProductName",
         sum(l."Quantity") AS "May units"
  FROM "Products" p
  LEFT JOIN order_lines l
    ON l."ProductID" = p."ProductID"
  WHERE p."CategoryID" = 4
    AND l."OrderDate" >= DATE '2024-05-01'
    AND l."OrderDate" <  DATE '2024-06-01'
  GROUP BY p."ProductName"
)
SELECT count(*) AS "Rows",
       count("May units") AS "Sold",
       sum("May units") AS "Units"
FROM report;
