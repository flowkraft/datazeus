-- Series 1 · Episode 10 · THE TRAP: AND binds tighter than OR.
-- This reads as "beverages, OR (condiments under 15)" — so Chai at 18.0000 and
-- Chang at 19.0000 sneak in. Four rows, two of them over the price cap.
SELECT "ProductName", "UnitPrice"
FROM "Products"
WHERE "CategoryID" = 1 OR "CategoryID" = 2 AND "UnitPrice" < 15;
