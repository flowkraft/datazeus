SELECT (SELECT ROUND(SUM(d."UnitPrice" * d."Quantity"
                 * (1 - d."Discount")), 2)
        FROM "Order Details" d
        JOIN "Orders" o
          ON o."OrderID" = d."OrderID"
        WHERE o."ShippedDate" IS NULL) AS "No ship date",
       (SELECT ROUND(SUM("UnitPrice" * "Quantity"
                 * (1 - "Discount")), 2)
        FROM "Order Details") AS "All sales";
