SELECT count(*) AS "count(*)",
       count(o."OrderID") AS "count(OrderID)"
FROM generate_series(
       TIMESTAMP '2024-06-01',
       TIMESTAMP '2024-06-30',
       INTERVAL '1 day') AS s(day)
LEFT JOIN "Orders" o
  ON date_trunc('day', o."OrderDate") = s.day;
