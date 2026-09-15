SELECT "OrderID", "ShippedDate"
FROM "Orders"
WHERE "ShippedDate" BETWEEN '2023-08-01'
                        AND '2023-09-01'
ORDER BY "ShippedDate";
