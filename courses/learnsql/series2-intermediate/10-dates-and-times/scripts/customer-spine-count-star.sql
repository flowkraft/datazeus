SELECT count(*) AS "count(*)",
       count(o."OrderID") AS "count(OrderID)"
FROM generate_series(
       TIMESTAMP '2024-01-01',
       TIMESTAMP '2024-12-01',
       INTERVAL '1 month') AS s(month)
LEFT JOIN "Orders" o
  ON date_trunc('month', o."OrderDate") = s.month
 AND o."CustomerID" = 'BRAM3';
