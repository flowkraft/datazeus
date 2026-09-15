SELECT "ShipCountry",
       count(*) AS "Orders",
       count(CASE WHEN "ShippedDate" IS NULL
             THEN 1 END) AS "No ship date"
FROM "Orders"
GROUP BY "ShipCountry"
ORDER BY "Orders" DESC, "ShipCountry";
