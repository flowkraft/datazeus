SELECT count(*) AS "Orders", sum("Freight") AS "TotalFreight"
FROM "Orders"
HAVING sum("Freight") > 3500;
