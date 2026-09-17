SELECT count(*) AS "Shipments",
       max("ShippedDate") AS "Last shipped"
FROM "Orders"
WHERE "ShippedDate" >= DATE '2024-11-01'
  AND "ShippedDate" <  DATE '2024-12-01';
