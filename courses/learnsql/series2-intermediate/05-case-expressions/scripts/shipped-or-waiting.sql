SELECT CASE
         WHEN "ShippedDate" IS NULL THEN 'waiting'
         ELSE 'shipped'
       END AS "Status",
       count(*) AS "Orders"
FROM "Orders"
GROUP BY "Status"
ORDER BY "Status";
