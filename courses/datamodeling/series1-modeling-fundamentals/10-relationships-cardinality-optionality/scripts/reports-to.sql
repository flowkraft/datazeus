SELECT e."FirstName" AS "Employee",
       m."FirstName" AS "Reports to"
FROM "Employees" e
LEFT JOIN "Employees" m
  ON m."EmployeeID" = e."ReportsTo"
ORDER BY e."EmployeeID";
