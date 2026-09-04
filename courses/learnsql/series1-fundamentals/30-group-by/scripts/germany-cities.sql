SELECT "ShipCity", count(*) AS "OrderCount"
FROM "Orders"
WHERE "ShipCountry" = 'Germany'
GROUP BY "ShipCity"
ORDER BY "OrderCount" DESC, "ShipCity";
