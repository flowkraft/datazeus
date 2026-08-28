-- Series 1 · Episode 15 · "the five most recently shipped orders" — and THE TWO
-- ENGINES DISAGREE, because 27 of our 79 orders have no "ShippedDate" at all.
--
--   DuckDB   sorts missing values LAST in both directions, so you get the five real
--            shipments: 6, 4, 79, 3, 78.
--   Postgres treats NULL as LARGER than any date, so DESC puts them FIRST — and you
--            get five orders that have never shipped at all.
--
-- Same query, same data, two answers. Neither engine is wrong; the query never said
-- where "missing" belongs. See recent-shipments-nulls-last.sql for the version that does.
SELECT "OrderID", "CustomerID", "ShippedDate"
FROM "Orders"
ORDER BY "ShippedDate" DESC
LIMIT 5;
