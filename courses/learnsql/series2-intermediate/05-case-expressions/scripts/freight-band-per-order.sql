SELECT "OrderID", "Freight",
       CASE
         WHEN "Freight" < 25 THEN 'light'
         WHEN "Freight" < 60 THEN 'medium'
         ELSE 'heavy'
       END AS "Band"
FROM "Orders"
ORDER BY "OrderID"
LIMIT 6;
