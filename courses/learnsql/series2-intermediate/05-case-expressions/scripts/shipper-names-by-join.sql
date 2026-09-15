SELECT s."CompanyName" AS "Shipper",
       count(*) AS "Orders"
FROM "Orders" o
JOIN "Shippers" s
  ON s."ShipperID" = o."ShipVia"
GROUP BY s."CompanyName"
ORDER BY s."CompanyName";
