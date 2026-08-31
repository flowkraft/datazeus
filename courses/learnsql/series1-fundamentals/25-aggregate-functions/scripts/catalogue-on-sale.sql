SELECT count(*) AS "On sale",
       min("UnitPrice") AS "Cheapest",
       max("UnitPrice") AS "Dearest",
       avg("UnitPrice") AS "Average"
FROM "Products"
WHERE "Discontinued" = false;
