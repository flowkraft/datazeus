SELECT "CompanyName", "City", "PostalCode"
FROM "Customers"
WHERE "City" IN (
  'Leipzig', 'Berlin', 'Stuttgart',
  'Graz', 'München'
)
ORDER BY "PostalCode";
