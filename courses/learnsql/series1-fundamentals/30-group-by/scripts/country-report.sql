SELECT "ShipCountry", count(*) AS "OrderCount",
       sum("Freight") AS "TotalFreight",
       max("Freight") AS "DearestDelivery"
FROM "Orders"
GROUP BY "ShipCountry"
ORDER BY "TotalFreight" DESC
LIMIT 5;
