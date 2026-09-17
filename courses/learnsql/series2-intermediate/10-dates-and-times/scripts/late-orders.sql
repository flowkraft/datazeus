SELECT "OrderID",
       "RequiredDate", "ShippedDate"
FROM "Orders"
WHERE "ShippedDate" > "RequiredDate"
  AND "ShippedDate" >= DATE '2024-12-01'
  AND "ShippedDate" <  DATE '2025-01-01'
ORDER BY "ShippedDate", "OrderID";
