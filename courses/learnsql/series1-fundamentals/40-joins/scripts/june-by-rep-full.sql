SELECT e."FirstName", o."OrderID", o."OrderDate"
FROM "Employees" e
FULL JOIN "Orders" o
  ON o."EmployeeID" = e."EmployeeID"
 AND o."OrderDate" >= DATE '2024-06-01'
ORDER BY e."FirstName", o."OrderDate";
