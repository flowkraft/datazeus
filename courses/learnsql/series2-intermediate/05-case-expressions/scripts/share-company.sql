SELECT count(*) AS "Orders",
       SUM(CASE WHEN "ShippedDate" IS NULL
           THEN 1 ELSE 0 END) AS "No ship date",
       ROUND(100.0 * SUM(CASE WHEN "ShippedDate" IS NULL
             THEN 1 ELSE 0 END) / count(*), 1) AS "Share %"
FROM "Orders";
