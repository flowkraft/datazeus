-- Series 1 · Episode 00 · real data, not toy data: five customers from Northwind.
-- ORDER BY is what makes "five" a defined set — without it any five rows would do.
SELECT "CompanyName", "Country"
FROM "Customers"
ORDER BY "CompanyName"
LIMIT 5;
