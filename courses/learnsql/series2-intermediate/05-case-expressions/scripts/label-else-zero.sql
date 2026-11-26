SELECT "OrderID",
       CASE
         WHEN "Freight" > 50 THEN 'heavy'
         ELSE 0
       END AS "Label"
FROM "Orders";
