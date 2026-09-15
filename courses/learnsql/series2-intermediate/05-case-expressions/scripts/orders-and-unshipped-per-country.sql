SELECT "ShipCountry",
       count(*) AS "Orders",
       SUM(CASE WHEN "ShippedDate" IS NULL
           THEN 1 ELSE 0 END) AS "Unshipped"
FROM "Orders"
GROUP BY "ShipCountry"
ORDER BY "Orders" DESC, "ShipCountry";
