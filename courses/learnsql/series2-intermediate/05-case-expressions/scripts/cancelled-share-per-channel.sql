SELECT "Channel",
       count(*) AS "Orders",
       SUM(CASE WHEN "Status" = 'Cancelled'
           THEN 1 ELSE 0 END) AS "Cancelled",
       ROUND(100.0 * SUM(CASE WHEN "Status" = 'Cancelled'
             THEN 1 ELSE 0 END) / count(*), 1) AS "Cancelled %"
FROM "Orders"
GROUP BY "Channel"
ORDER BY "Orders" DESC;
