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
SELECT CASE WHEN "Days" > 90 THEN 'gone quiet'
            ELSE 'active' END AS "Status",
       count(*) AS "Customers"
FROM days_quiet
GROUP BY "Status"
ORDER BY "Status";
