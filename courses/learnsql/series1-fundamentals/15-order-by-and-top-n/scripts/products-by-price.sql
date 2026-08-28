-- Series 1 · Episode 15 · ORDER BY, one column, no direction word.
-- Ascending is the default, so this is cheapest first: Guarana Fantastica at 4.5000.
-- You may write ASC and mean exactly the same thing; almost nobody does.
SELECT "ProductName", "UnitPrice"
FROM "Products"
ORDER BY "UnitPrice";
