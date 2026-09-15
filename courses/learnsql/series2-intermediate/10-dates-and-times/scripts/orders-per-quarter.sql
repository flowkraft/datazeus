SELECT date_trunc('quarter', "OrderDate")
         AS "Quarter",
       count(*) AS "Orders",
       min("OrderDate") AS "First order",
       max("OrderDate") AS "Last order"
FROM "Orders"
GROUP BY date_trunc('quarter', "OrderDate")
ORDER BY "Quarter";
