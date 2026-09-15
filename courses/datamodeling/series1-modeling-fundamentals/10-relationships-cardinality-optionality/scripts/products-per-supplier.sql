SELECT s."CompanyName",
       count(*) AS "Products"
FROM "Suppliers" s
JOIN "Products" p
  ON p."SupplierID" = s."SupplierID"
GROUP BY s."SupplierID", s."CompanyName"
ORDER BY s."SupplierID";
