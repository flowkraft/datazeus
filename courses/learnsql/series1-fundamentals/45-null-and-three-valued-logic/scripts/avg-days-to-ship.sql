SELECT round(avg(
         CAST("ShippedDate" AS DATE)
       - CAST("OrderDate" AS DATE)
       ), 1) AS "AvgDaysToShip"
FROM "Orders";
