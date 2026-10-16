SELECT "OrderID",
       CASE
         WHEN "Freight" > 50 THEN 'heavy'
       END AS "Label"
FROM "Orders"
WHERE "Label" = 'heavy';
