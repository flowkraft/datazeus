SELECT CASE
         WHEN "ShippedDate" IS NULL THEN 'no ship date'
         ELSE 'shipped'
       END AS "Status",
       count(*) AS "Orders"
FROM "Orders"
GROUP BY "Status"
ORDER BY "Orders" DESC;
