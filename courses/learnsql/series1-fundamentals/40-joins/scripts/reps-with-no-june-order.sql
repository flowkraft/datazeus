SELECT e."FirstName", e."LastName"
FROM "Employees" e
LEFT JOIN "Orders" o
  ON o."EmployeeID" = e."EmployeeID"
 AND o."OrderDate" >= DATE '2024-06-01'
WHERE o."OrderID" IS NULL
ORDER BY e."FirstName";
