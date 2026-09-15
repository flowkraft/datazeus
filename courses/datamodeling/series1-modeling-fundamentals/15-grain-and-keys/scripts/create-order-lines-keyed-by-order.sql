CREATE TABLE practice."Order Details" (
  "OrderID"   INTEGER PRIMARY KEY,
  "ProductID" INTEGER,
  "UnitPrice" DECIMAL(19,4),
  "Quantity"  SMALLINT,
  "Discount"  DECIMAL(8,4)
);
