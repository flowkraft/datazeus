-- Series 1 · Episode 15 · the SAME bug, one level down — and this one has an ORDER BY.
-- "Our five cheapest lines, for the promo." Two products cost exactly 12.5000:
-- Scottish Longbreads and Gorgonzola Telino. Only one of them can be fifth, and
-- NOTHING IN THIS QUERY DECIDES WHICH. The engine is free to return either.
--
-- So the list you send on Monday and the list you send on Tuesday can differ by one
-- line, both correct, with no error either time. See cheapest-5-tiebreak.sql for the fix.
SELECT "ProductName", "UnitPrice"
FROM "Products"
ORDER BY "UnitPrice"
LIMIT 5;
