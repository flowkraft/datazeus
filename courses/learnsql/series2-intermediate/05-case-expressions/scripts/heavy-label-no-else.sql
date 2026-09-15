SELECT CASE
         WHEN "Freight" > 50 THEN 'heavy'
       END AS "Label",
       count(*) AS "Orders"
FROM "Orders"
GROUP BY "Label"
ORDER BY "Label" NULLS LAST;
