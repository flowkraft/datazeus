SELECT "EmployeeID", count(*) AS "OrderCount"
FROM "Orders"
GROUP BY "EmployeeID"
ORDER BY "OrderCount" DESC;
