WITH june_lines AS (
  SELECT d."ProductID", d."Quantity"
  FROM "Order Details" d
  JOIN "Orders" o
    ON o."OrderID" = d."OrderID"
  WHERE o."OrderDate" >= DATE '2024-06-01'
    AND o."OrderDate" <  DATE '2024-07-01'
),
june_units AS (
  SELECT "ProductID",
         sum("Quantity") AS "June units"
  FROM june_lines
  GROUP BY "ProductID"
),
report AS (
  SELECT p."ProductName", u."June units"
  FROM "Products" p
  LEFT JOIN june_units u
    ON u."ProductID" = p."ProductID"
)
SELECT count(*) AS "Rows",
       count("June units") AS "Sold",
       sum("June units") AS "Units"
FROM report;
