-- Series 1 · Episode 10 · numbers are compared without quotes.
-- 'Germany' needs quotes because it is text; 10 is a number and stands alone.
SELECT "ProductName", "UnitPrice"
FROM "Products"
WHERE "UnitPrice" < 10;
