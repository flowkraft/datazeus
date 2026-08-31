SELECT "OrderID", "CustomerID", "OrderDate"
FROM "Orders"
WHERE "ShippedDate" IS NULL
ORDER BY "OrderDate"
LIMIT 5;
