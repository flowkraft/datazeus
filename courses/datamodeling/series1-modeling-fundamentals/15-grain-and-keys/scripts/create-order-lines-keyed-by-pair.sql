CREATE TABLE practice."Order Details" (
  "OrderID"   INTEGER,
  "ProductID" INTEGER,
  "UnitPrice" DECIMAL(19,4),
  "Quantity"  SMALLINT,
  "Discount"  DECIMAL(8,4),
  PRIMARY KEY ("OrderID", "ProductID")
);
