SELECT "ShipCity", count(*) AS "OrderCount"
FROM "Orders"
GROUP BY "ShipCity"
ORDER BY "OrderCount" DESC, "ShipCity"
LIMIT 5;
