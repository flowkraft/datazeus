SELECT ROUND(AVG(CAST("ShippedDate" AS DATE)
         - CAST("OrderDate" AS DATE)), 1)
         AS "Average days",
       max(CAST("ShippedDate" AS DATE)
         - CAST("OrderDate" AS DATE))
         AS "Longest"
FROM "Orders";
