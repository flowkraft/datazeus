SELECT "OrderID", "ShippedDate"
FROM "Orders"
WHERE "ShippedDate" BETWEEN DATE '2023-08-01'
                        AND DATE '2023-09-01'
ORDER BY "ShippedDate";
