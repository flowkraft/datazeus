-- Series 1 · Episode 10 · AND: every condition must pass — each AND narrows.
-- Category 1 is Beverages (episode 05 showed the Categories table). One row survives.
SELECT "ProductName", "UnitPrice"
FROM "Products"
WHERE "CategoryID" = 1
  AND "UnitPrice" < 10;
