-- Series 1 · Episode 15 · THE FIX: a second sort key. Read it left to right —
-- sort by price; where two prices are equal, sort those rows by name.
--
--   Guarana Fantastica    4.5000
--   Filo Mix              7.0000
--   Aniseed Syrup        10.0000
--   Gorgonzola Telino    12.5000   <- G before S decides the tie, every single run
--   Scottish Longbreads  12.5000
--
-- The rule worth keeping: if the result is going to somebody, break the tie. A second
-- key costs you six characters and makes the answer repeatable.
SELECT "ProductName", "UnitPrice"
FROM "Products"
ORDER BY "UnitPrice", "ProductName"
LIMIT 5;
