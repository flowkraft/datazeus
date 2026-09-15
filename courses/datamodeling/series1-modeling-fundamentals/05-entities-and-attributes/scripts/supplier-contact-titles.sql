SELECT "ContactTitle",
       count(*) AS "Suppliers"
FROM "Suppliers"
GROUP BY "ContactTitle"
ORDER BY "Suppliers" DESC, "ContactTitle";
