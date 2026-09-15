SELECT "CustomerID",
       count(*) AS "Orders"
FROM "Orders"
GROUP BY "CustomerID"
ORDER BY "Orders", "CustomerID"
LIMIT 3;
