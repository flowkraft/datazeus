SELECT e."FirstName" AS "Rep",
       s."CompanyName" AS "Courier",
       count(*) AS "Orders",
       ROUND(SUM(v."Order value"), 2) AS "Sales",
       SUM(o."Freight") AS "Freight"
FROM "Orders" o
JOIN (SELECT "OrderID",
             SUM("UnitPrice" * "Quantity"
               * (1 - "Discount")) AS "Order value"
      FROM "Order Details"
      GROUP BY "OrderID") AS v
  ON v."OrderID" = o."OrderID"
JOIN "Employees" e
  ON e."EmployeeID" = o."EmployeeID"
JOIN "Shippers" s
  ON s."ShipperID" = o."ShipVia"
WHERE o."ShippedDate" IS NULL
GROUP BY e."FirstName", s."CompanyName"
ORDER BY "Sales" DESC;
