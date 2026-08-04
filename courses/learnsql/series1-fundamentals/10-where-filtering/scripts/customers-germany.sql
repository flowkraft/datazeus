-- Series 1 · Episode 10 · WHERE keeps only the rows that pass the test.
-- 25 customers in the table; exactly 11 of them are in Germany.
SELECT "CompanyName", "City"
FROM "Customers"
WHERE "Country" = 'Germany';
