SELECT "CompanyName",
       COALESCE("Region", '(none)')
         AS "With COALESCE",
       CASE
         WHEN "Region" IS NULL THEN '(none)'
         ELSE "Region"
       END AS "With CASE"
FROM "Customers"
ORDER BY "CustomerID"
LIMIT 4;
