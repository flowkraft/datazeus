SELECT ROUND(AVG("UnitPrice" * "Quantity"
         * (1 - "Discount")), 2) AS "Average"
FROM "Order Details";
