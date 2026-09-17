SELECT "CompanyName", "Country",
       COALESCE("Region", '(none)')
         AS "With COALESCE",
       CASE
         WHEN "Region" IS NULL THEN '(none)'
         ELSE "Region"
       END AS "With CASE"
FROM "Customers"
WHERE "Country" IN ('Canada', 'Poland')
ORDER BY "CustomerID";
