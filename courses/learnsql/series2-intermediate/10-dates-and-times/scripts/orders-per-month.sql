SELECT date_trunc('month', "OrderDate")
         AS "Month",
       count(*) AS "Orders"
FROM "Orders"
GROUP BY date_trunc('month', "OrderDate")
ORDER BY "Month";
