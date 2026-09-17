SELECT count(*) AS "Shipments",
       min("ShippedDate") AS "First shipped"
FROM "Orders"
WHERE "ShippedDate" BETWEEN DATE '2024-12-01'
                        AND DATE '2025-01-01';
