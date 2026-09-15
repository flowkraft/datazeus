SELECT CASE
         WHEN "Freight" < 25 THEN 'light'
         WHEN "Freight" < 60 THEN 'medium'
         ELSE 'heavy'
       END AS "Band",
       count(*) AS "Orders"
FROM "Orders"
GROUP BY "Band"
ORDER BY min("Freight");
