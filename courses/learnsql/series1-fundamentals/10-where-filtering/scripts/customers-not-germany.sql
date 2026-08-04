-- Series 1 · Episode 10 · <> means "not equal" (!= is the same operator).
-- Everyone who is NOT in Germany: the other 14 customers.
SELECT "CompanyName", "Country"
FROM "Customers"
WHERE "Country" <> 'Germany';
