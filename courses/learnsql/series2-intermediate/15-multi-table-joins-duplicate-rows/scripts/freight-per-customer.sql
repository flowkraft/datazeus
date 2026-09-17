SELECT c."CompanyName",
       SUM(o."Freight") AS "Freight"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
WHERE o."OrderDate" >= DATE '2024-05-01'
  AND o."OrderDate" <  DATE '2024-06-01'
GROUP BY c."CompanyName"
ORDER BY "Freight" DESC
LIMIT 5;
