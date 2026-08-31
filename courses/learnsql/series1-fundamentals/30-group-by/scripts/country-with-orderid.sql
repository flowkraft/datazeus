SELECT "ShipCountry", "OrderID", count(*) AS "OrderCount"
FROM "Orders"
GROUP BY "ShipCountry";
