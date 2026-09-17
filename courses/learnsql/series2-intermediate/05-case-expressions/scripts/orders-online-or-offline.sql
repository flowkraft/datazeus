SELECT CASE
         WHEN "Channel" IN ('Web', 'EDI') THEN 'online'
         ELSE 'offline'
       END AS "Channel type",
       count(*) AS "Orders"
FROM "Orders"
GROUP BY "Channel type"
ORDER BY "Orders" DESC;
