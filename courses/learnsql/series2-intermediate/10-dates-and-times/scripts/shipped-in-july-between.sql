SELECT "OrderID", "ShippedDate"
FROM "Orders"
WHERE "ShippedDate" BETWEEN DATE '2023-07-01'
                        AND DATE '2023-08-01'
ORDER BY "ShippedDate";
