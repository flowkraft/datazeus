SELECT "ShipCountry",
       count(*) AS "Orders",
       count(CASE WHEN "ShippedDate" IS NULL
             THEN 1 END) AS "Unshipped"
FROM "Orders"
GROUP BY "ShipCountry"
ORDER BY "Orders" DESC, "ShipCountry";
