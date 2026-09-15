INSERT INTO practice."Order Details"
SELECT "OrderID", "ProductID", "UnitPrice",
       "Quantity", "Discount"
FROM "Order Details"
ORDER BY "OrderID", "ProductID";
