SELECT count(*) AS "Rows",
       count(DISTINCT
             ("OrderID", "ProductID"))
         AS "Pairs"
FROM practice."Order Details";
