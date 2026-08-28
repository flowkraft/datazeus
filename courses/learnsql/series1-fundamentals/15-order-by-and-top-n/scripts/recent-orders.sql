-- Series 1 · Episode 15 · the hands-on query, and the single most-run shape in any
-- operational system: "what came in most recently?"
--
--   7   BERGS   2024-06-12   22.7500
--   5   ANATR   2024-06-10   11.6100
--   6   AROUT   2024-06-07   45.5000
--   4   ALFKI   2024-06-05   32.3800
--   79  OTTIK   2024-05-26   57.1300
--
-- "OrderDate" has no missing values in Northwind (79 of 79 are filled), so this one
-- needs no NULLS clause — unlike "ShippedDate". Checking that is part of the job.
SELECT "OrderID", "CustomerID", "OrderDate", "Freight"
FROM "Orders"
ORDER BY "OrderDate" DESC
LIMIT 5;
