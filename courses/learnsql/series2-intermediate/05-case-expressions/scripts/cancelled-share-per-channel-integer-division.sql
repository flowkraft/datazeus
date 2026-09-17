SELECT "Channel",
       count(*) AS "Orders",
       SUM(CASE WHEN "Status" = 'Cancelled'
           THEN 1 ELSE 0 END) AS "Cancelled",
       SUM(CASE WHEN "Status" = 'Cancelled'
           THEN 1 ELSE 0 END) / count(*) AS "Share"
FROM "Orders"
GROUP BY "Channel"
ORDER BY "Orders" DESC;
