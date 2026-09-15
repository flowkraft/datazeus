SELECT s.day AS "Day",
       count(o."OrderID") AS "Orders"
FROM generate_series(
       TIMESTAMP '2024-06-01',
       TIMESTAMP '2024-06-30',
       INTERVAL '1 day') AS s(day)
LEFT JOIN "Orders" o
  ON date_trunc('day', o."OrderDate") = s.day
GROUP BY s.day
ORDER BY s.day;
