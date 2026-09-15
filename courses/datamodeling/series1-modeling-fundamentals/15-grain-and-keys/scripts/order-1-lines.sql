SELECT "OrderID", "ProductID",
       "UnitPrice", "Quantity", "Discount"
FROM "Order Details"
WHERE "OrderID" = 1
ORDER BY "ProductID";
