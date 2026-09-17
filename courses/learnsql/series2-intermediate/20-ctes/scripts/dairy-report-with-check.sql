WITH may_lines AS (
  SELECT d."ProductID", d."Quantity"
  FROM "Order Details" d
  JOIN "Orders" o
    ON o."OrderID" = d."OrderID"
  WHERE o."OrderDate" >= DATE '2024-05-01'
    AND o."OrderDate" <  DATE '2024-06-01'
),
may_units AS (
  SELECT "ProductID",
         sum("Quantity") AS "May units"
  FROM may_lines
  GROUP BY "ProductID"
),
report AS (
  SELECT p."ProductName", u."May units"
  FROM "Products" p
  LEFT JOIN may_units u
    ON u."ProductID" = p."ProductID"
  WHERE p."CategoryID" = 4
)
SELECT count(*) AS "Rows",
       count("May units") AS "Sold",
       sum("May units") AS "Units"
FROM report;
