-- Series 1 · Episode 10 · BETWEEN keeps a range — and BOTH ends are included.
-- Aniseed Syrup costs exactly 10.0000 and it is in: BETWEEN 10 AND 20 means >= 10 AND <= 20.
SELECT "ProductName", "UnitPrice"
FROM "Products"
WHERE "UnitPrice" BETWEEN 10 AND 20;
