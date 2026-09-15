SELECT "ProductID",
       count(*) AS "Suppliers"
FROM practice."Product Suppliers"
GROUP BY "ProductID"
HAVING count(*) > 1;
