SELECT count(*) AS "Slow orders"
FROM "Orders"
WHERE CAST("ShippedDate" AS DATE)
      - CAST("OrderDate" AS DATE) > 7;
