SELECT CASE "ShipVia"
         WHEN 1 THEN 'Speedy Express'
         WHEN 2 THEN 'United Package'
         WHEN 3 THEN 'Federal Shipping'
       END AS "Shipper",
       count(*) AS "Orders"
FROM "Orders"
GROUP BY "Shipper"
ORDER BY "Shipper";
