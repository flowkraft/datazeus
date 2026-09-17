SELECT date_trunc('week', "OrderDate")
         AS "Week",
       count(*) AS "Orders",
       min("OrderDate") AS "First order",
       max("OrderDate") AS "Last order"
FROM "Orders"
GROUP BY date_trunc('week', "OrderDate")
ORDER BY "Week";
