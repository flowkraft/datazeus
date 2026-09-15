SELECT SUM(DISTINCT "Freight") AS "SUM(DISTINCT)",
       SUM("Freight") AS "Real total"
FROM (VALUES (1, 10.00),
             (2, 10.00),
             (3, 25.50)) AS t("OrderID", "Freight");
