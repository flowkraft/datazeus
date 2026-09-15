SELECT "ContactTitle",
       count(*) AS "Customers"
FROM "Customers"
GROUP BY "ContactTitle"
ORDER BY "Customers" DESC, "ContactTitle";
