SELECT (SELECT ROUND(SUM(d."UnitPrice" * d."Quantity"
                 * (1 - d."Discount")), 2)
        FROM "Order Details" d
        JOIN "Orders" o
          ON o."OrderID" = d."OrderID"
        WHERE o."OrderDate" >= DATE '2024-01-01'
          AND o."OrderDate" <  DATE '2025-01-01') AS "Ordered 2024",
       (SELECT ROUND(SUM("Amount"), 2)
        FROM "Invoices"
        WHERE "InvoiceDate" >= DATE '2024-01-01'
          AND "InvoiceDate" <  DATE '2025-01-01') AS "Invoiced 2024";
