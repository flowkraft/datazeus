SELECT count(*) AS "Orders"
FROM "Orders"
WHERE "OrderDate" >= CURRENT_DATE
                     - INTERVAL '30 days';
