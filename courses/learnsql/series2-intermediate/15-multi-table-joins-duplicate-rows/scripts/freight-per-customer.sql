SELECT c."CompanyName",
       SUM(o."Freight") AS "Freight"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
GROUP BY c."CompanyName"
ORDER BY "Freight" DESC
LIMIT 5;
