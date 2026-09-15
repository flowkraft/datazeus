SELECT c."CompanyName",
       CAST((SELECT max("OrderDate")
             FROM "Orders") AS DATE)
       - CAST(max(o."OrderDate") AS DATE)
         AS "Days since last order"
FROM "Customers" c
JOIN "Orders" o
  ON o."CustomerID" = c."CustomerID"
GROUP BY c."CompanyName"
ORDER BY "Days since last order" DESC;
