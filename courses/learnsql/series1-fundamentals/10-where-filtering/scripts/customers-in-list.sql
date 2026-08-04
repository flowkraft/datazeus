-- Series 1 · Episode 10 · IN: one test against a whole list of values.
-- The same as three = tests glued with OR, but readable. Five customers match.
SELECT "CompanyName", "Country"
FROM "Customers"
WHERE "Country" IN ('Mexico', 'Venezuela', 'Argentina');
