SELECT "Region", count(*) AS "Customers"
FROM "Customers"
GROUP BY "Region"
ORDER BY "Customers" DESC, "Region";
