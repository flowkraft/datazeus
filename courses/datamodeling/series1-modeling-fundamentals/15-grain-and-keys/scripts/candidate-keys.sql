SELECT count(*) AS "Customers",
       count(DISTINCT "CustomerID")
         AS "IDs",
       count(DISTINCT "CompanyName")
         AS "Names"
FROM "Customers";
