-- Series 1 · Episode 10 · the same range with strict comparisons excludes the ends.
-- Aniseed Syrup at exactly 10.0000 drops out: nine rows become eight.
SELECT "ProductName", "UnitPrice"
FROM "Products"
WHERE "UnitPrice" > 10
  AND "UnitPrice" < 20;
