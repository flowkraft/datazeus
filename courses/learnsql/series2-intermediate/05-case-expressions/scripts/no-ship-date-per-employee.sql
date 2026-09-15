SELECT e."FirstName",
       count(*) AS "Orders",
       SUM(CASE
             WHEN o."ShippedDate" IS NULL THEN 1
             ELSE 0
           END) AS "No ship date"
FROM "Orders" o
JOIN "Employees" e
  ON e."EmployeeID" = o."EmployeeID"
GROUP BY e."FirstName"
ORDER BY e."FirstName";
