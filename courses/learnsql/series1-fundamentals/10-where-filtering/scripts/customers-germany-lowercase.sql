-- Series 1 · Episode 10 · text comparison is EXACT, capitals included.
-- 'germany' matches nothing — the column holds 'Germany'. Zero rows, no error.
SELECT "CompanyName", "City"
FROM "Customers"
WHERE "Country" = 'germany';
