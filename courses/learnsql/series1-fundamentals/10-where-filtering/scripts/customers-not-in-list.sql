-- Series 1 · Episode 10 · NOT IN: everyone the list does NOT name.
-- 25 customers minus the 5 from the list = 20 rows.
SELECT count(*)
FROM "Customers"
WHERE "Country" NOT IN ('Mexico', 'Venezuela', 'Argentina');
