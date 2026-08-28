-- Series 1 · Episode 15 · WHERE from episode 10 + ORDER BY from this one, and the
-- result is a query somebody in your company actually wants: the shipping backlog,
-- oldest first, so the worst one is the first thing you read.
--
--   8   ALFKI   2022-12-05    <- placed, never shipped, and it is the oldest we have
--   11  AROUT   2022-12-26
--   14  BONAP   2023-01-19
--   17  DUMON   2023-02-12
--   20  FRANK   2023-03-05
--
-- IS NULL, never = NULL. Missing needs its own test — Series 1 · 45 explains why
-- = NULL quietly matches nothing at all. For now, use IS NULL and trust it.
SELECT "OrderID", "CustomerID", "OrderDate"
FROM "Orders"
WHERE "ShippedDate" IS NULL
ORDER BY "OrderDate"
LIMIT 5;
