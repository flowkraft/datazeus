SELECT "OrderID", "CustomerID", "OrderDate",
       CAST((SELECT max("OrderDate")
             FROM "Orders") AS DATE)
       - CAST("OrderDate" AS DATE)
         AS "Days waiting"
FROM "Orders"
WHERE "ShippedDate" IS NULL
ORDER BY "Days waiting" DESC;
