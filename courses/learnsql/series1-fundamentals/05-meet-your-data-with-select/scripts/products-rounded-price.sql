-- Series 1 · Episode 05 · ROUND changes the DISPLAY, not what is stored.
-- UnitPrice still holds four decimals; only this answer shows two.
SELECT "ProductName", ROUND("UnitPrice", 2)
FROM "Products"
LIMIT 5;
