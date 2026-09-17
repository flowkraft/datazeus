SELECT s."CompanyName" AS "Courier",
       count(*) AS "Orders",
       SUM(CASE WHEN o."Status" = 'Shipped' AND o."ShippedDate" IS NULL
           THEN 1 ELSE 0 END) AS "Shipped, no date",
       ROUND(100.0 * SUM(CASE WHEN o."Status" = 'Shipped' AND o."ShippedDate" IS NULL
             THEN 1 ELSE 0 END) / count(*), 1) AS "Share %"
FROM "Orders" o
JOIN "Shippers" s ON s."ShipperID" = o."ShipVia"
WHERE o."OrderDate" >= DATE '2024-01-01'
  AND o."OrderDate" < DATE '2025-01-01'
GROUP BY s."CompanyName"
ORDER BY s."CompanyName";
