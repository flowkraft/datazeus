SELECT EXTRACT(MONTH FROM "OrderDate")
         AS "Month",
       count(*) AS "Orders"
FROM "Orders"
GROUP BY EXTRACT(MONTH FROM "OrderDate")
ORDER BY "Month";
