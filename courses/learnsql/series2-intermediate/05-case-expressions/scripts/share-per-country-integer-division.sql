SELECT "ShipCountry",
       count(*) AS "Orders",
       SUM(CASE WHEN "ShippedDate" IS NULL
           THEN 1 ELSE 0 END) AS "No ship date",
       SUM(CASE WHEN "ShippedDate" IS NULL
           THEN 1 ELSE 0 END) / count(*) AS "Share"
FROM "Orders"
GROUP BY "ShipCountry"
ORDER BY "Orders" DESC, "ShipCountry";
