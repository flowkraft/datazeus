SELECT "OrderID", "ShippedDate"
FROM "Orders"
WHERE "ShippedDate" >= '2023-07-01'
  AND "ShippedDate" <  '2023-08-01'
ORDER BY "ShippedDate";
