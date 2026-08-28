SELECT count(*)
FROM "Customers"
WHERE "Country" NOT IN ('Mexico', 'Venezuela', 'Argentina');
