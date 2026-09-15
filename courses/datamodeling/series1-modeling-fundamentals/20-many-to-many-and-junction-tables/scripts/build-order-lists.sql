CREATE TABLE practice."Order Lists" AS
SELECT "OrderID",
       string_agg(
         CAST("ProductID" AS VARCHAR),
         ',' ORDER BY "ProductID")
         AS "Products"
FROM "Order Details"
GROUP BY "OrderID";
