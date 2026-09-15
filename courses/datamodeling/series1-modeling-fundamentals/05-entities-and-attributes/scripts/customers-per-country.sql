SELECT "Country",
       count(*) AS "Customers"
FROM "Customers"
GROUP BY "Country"
ORDER BY "Customers" DESC, "Country";
