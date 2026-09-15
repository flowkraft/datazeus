SELECT "OrderID", "OrderDate", "ShippedDate",
       CAST("ShippedDate" AS DATE)
       - CAST("OrderDate" AS DATE) AS "Days"
FROM "Orders"
ORDER BY "OrderID"
LIMIT 5;
