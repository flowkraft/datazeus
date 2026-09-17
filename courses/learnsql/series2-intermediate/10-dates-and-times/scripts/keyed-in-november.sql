SELECT SUM(CASE WHEN "CreatedAt" BETWEEN DATE '2024-11-01'
                                     AND DATE '2024-11-30'
           THEN 1 ELSE 0 END) AS "Ends on the 30th",
       SUM(CASE WHEN "CreatedAt" >= DATE '2024-11-01'
                 AND "CreatedAt" <  DATE '2024-12-01'
           THEN 1 ELSE 0 END) AS "Half-open"
FROM "Orders";
