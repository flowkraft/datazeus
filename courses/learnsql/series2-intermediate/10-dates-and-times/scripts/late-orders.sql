SELECT "OrderID",
       "RequiredDate", "ShippedDate"
FROM "Orders"
WHERE "ShippedDate" > "RequiredDate";
