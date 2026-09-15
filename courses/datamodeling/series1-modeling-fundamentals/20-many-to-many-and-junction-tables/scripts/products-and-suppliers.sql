SELECT count(*) AS "Products",
       count(DISTINCT "SupplierID")
         AS "Suppliers"
FROM "Products";
