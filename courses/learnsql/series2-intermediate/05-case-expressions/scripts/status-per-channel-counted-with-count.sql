SELECT "Channel",
       count(*) AS "Orders",
       count(CASE WHEN "Status" = 'Shipped'
             THEN 1 END) AS "Shipped",
       count(CASE WHEN "Status" = 'Cancelled'
             THEN 1 END) AS "Cancelled",
       count(CASE WHEN "Status" = 'Open'
             THEN 1 END) AS "Open"
FROM "Orders"
GROUP BY "Channel"
ORDER BY "Orders" DESC;
