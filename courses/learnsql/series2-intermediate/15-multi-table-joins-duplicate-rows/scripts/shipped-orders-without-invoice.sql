SELECT count(*) AS "Orders",
       ROUND(SUM(v."Order value"), 2) AS "Value"
FROM "Orders" o
JOIN (SELECT "OrderID",
             SUM("UnitPrice" * "Quantity"
               * (1 - "Discount")) AS "Order value"
      FROM "Order Details"
      GROUP BY "OrderID") AS v
  ON v."OrderID" = o."OrderID"
LEFT JOIN "Invoices" i
  ON i."OrderID" = o."OrderID"
WHERE o."Status" = 'Shipped'
  AND o."OrderDate" >= DATE '2024-01-01'
  AND o."OrderDate" <  DATE '2025-01-01'
  AND i."InvoiceID" IS NULL;
