SELECT count(*) AS "Order lines",
       count(DISTINCT
             ("OrderID", "ProductID"))
         AS "Pairs"
FROM "Order Details";
