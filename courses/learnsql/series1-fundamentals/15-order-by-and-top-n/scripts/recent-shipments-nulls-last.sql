SELECT "OrderID", "CustomerID", "ShippedDate"
FROM "Orders"
ORDER BY "ShippedDate" DESC NULLS LAST
LIMIT 5;
