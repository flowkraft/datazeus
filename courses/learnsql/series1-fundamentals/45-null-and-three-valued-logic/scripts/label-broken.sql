SELECT "City", "Region", "City" || ', ' || "Region" AS "Label"
FROM "Customers"
ORDER BY "CustomerID"
LIMIT 4;
