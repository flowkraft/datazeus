SELECT count(*) AS "Orders"
FROM "Orders"
WHERE NOT (
  "ShippedDate" >= DATE '2024-01-01'
);
