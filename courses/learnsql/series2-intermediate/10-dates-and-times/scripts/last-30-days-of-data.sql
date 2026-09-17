SELECT count(*) AS "Orders",
       min("OrderDate") AS "From",
       max("OrderDate") AS "To"
FROM "Orders"
WHERE "OrderDate" >=
      (SELECT max("OrderDate") FROM "Orders")
      - INTERVAL '30 days';
