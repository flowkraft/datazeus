SELECT s.month AS "Month",
       count(o."OrderID") AS "Orders"
FROM generate_series(
       TIMESTAMP '2024-01-01',
       TIMESTAMP '2024-12-01',
       INTERVAL '1 month') AS s(month)
LEFT JOIN "Orders" o
  ON date_trunc('month', o."OrderDate") = s.month
 AND o."CustomerID" = 'BRAM3'
GROUP BY s.month
ORDER BY s.month;
