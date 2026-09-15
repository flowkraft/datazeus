SELECT date_trunc('quarter', "OrderDate")
         AS "Quarter",
       count(*) AS "Orders",
       SUM(CASE WHEN "ShippedDate" IS NULL
           THEN 1 ELSE 0 END) AS "No ship date"
FROM "Orders"
GROUP BY date_trunc('quarter', "OrderDate")
ORDER BY "Quarter";
