SELECT ROUND(SUM("UnitPrice" * "Quantity"
         * (1 - "Discount")), 2) AS "Total"
FROM "Order Details";
