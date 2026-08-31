SELECT p."ProductName", "UnitPrice", d."Quantity"
FROM "Order Details" d
JOIN "Products" p
  ON p."ProductID" = d."ProductID";
