SELECT "City" || ', ' || COALESCE("Region", 'no region') AS "Label"
FROM "Customers"
ORDER BY "CustomerID"
LIMIT 4;
