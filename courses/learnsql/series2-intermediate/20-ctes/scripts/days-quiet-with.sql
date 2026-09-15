WITH last_order AS (
  SELECT "CustomerID",
         max("OrderDate") AS "Last order"
  FROM "Orders"
  GROUP BY "CustomerID"
),
days_quiet AS (
  SELECT "CustomerID",
         CAST((SELECT max("OrderDate")
               FROM "Orders") AS DATE)
         - CAST("Last order" AS DATE)
           AS "Days"
  FROM last_order
)
SELECT c."CompanyName", q."Days",
       CASE WHEN q."Days" > 90 THEN 'gone quiet'
            ELSE 'active' END AS "Status"
FROM days_quiet q
JOIN "Customers" c
  ON c."CustomerID" = q."CustomerID"
ORDER BY q."Days" DESC
LIMIT 5;
