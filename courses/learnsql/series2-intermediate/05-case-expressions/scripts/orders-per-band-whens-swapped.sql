SELECT CASE
         WHEN "Freight" < 60 THEN 'medium'
         WHEN "Freight" < 25 THEN 'light'
         ELSE 'heavy'
       END AS "Band",
       count(*) AS "Orders"
FROM "Orders"
GROUP BY "Band"
ORDER BY min("Freight");
