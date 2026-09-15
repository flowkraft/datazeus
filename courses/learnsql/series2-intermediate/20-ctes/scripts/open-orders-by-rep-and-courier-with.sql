WITH open_orders AS (
  SELECT "OrderID", "EmployeeID", "ShipVia", "Freight"
  FROM "Orders"
  WHERE "ShippedDate" IS NULL
),
order_value AS (
  SELECT o."OrderID",
         SUM(d."UnitPrice" * d."Quantity"
           * (1 - d."Discount")) AS "Order value"
  FROM open_orders o
  JOIN "Order Details" d ON d."OrderID" = o."OrderID"
  GROUP BY o."OrderID"
)
SELECT e."FirstName" AS "Rep",
       s."CompanyName" AS "Courier",
       count(*) AS "Orders",
       ROUND(SUM(v."Order value"), 2) AS "Sales",
       SUM(o."Freight") AS "Freight"
FROM open_orders o
JOIN order_value v ON v."OrderID" = o."OrderID"
JOIN "Employees" e ON e."EmployeeID" = o."EmployeeID"
JOIN "Shippers" s ON s."ShipperID" = o."ShipVia"
GROUP BY e."FirstName", s."CompanyName"
ORDER BY "Sales" DESC;
