SELECT "Band", count(*) AS "Orders"
FROM (SELECT CASE
               WHEN "Freight" < 25 THEN 'light'
               WHEN "Freight" < 60 THEN 'medium'
               ELSE 'heavy' END AS "Band"
      FROM "Orders") AS t
GROUP BY "Band"
ORDER BY CASE WHEN "Band" = 'heavy' THEN 1
              WHEN "Band" = 'medium' THEN 2 ELSE 3 END;
