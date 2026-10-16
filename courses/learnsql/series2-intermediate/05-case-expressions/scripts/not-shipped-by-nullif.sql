SELECT "Channel",
       count(*) AS "Orders",
       count(NULLIF("Status", 'Shipped')) AS "Not shipped"
FROM "Orders"
GROUP BY "Channel"
ORDER BY "Orders" DESC;
