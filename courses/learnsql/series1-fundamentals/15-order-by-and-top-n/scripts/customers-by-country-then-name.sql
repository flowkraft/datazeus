-- Series 1 · Episode 15 · two sort keys doing the job they were made for: a list a
-- human is going to read. Country groups it, name orders each group. Read left to
-- right — the FIRST key decides, and the second only gets a say when the first ties.
--
--   Cactus Comidas para llevar   Argentina
--   Ernst Handel                 Austria
--   Bon app'                     France
--   Du monde entier              France
--   Alfreds Futterkiste          Germany
--   Blauer See Delikatessen      Germany
--   Die Wandernde Kuh            Germany
--   Drachenblut Delikatessen     Germany
--
-- Swap the two keys and you get a different report — every customer alphabetically,
-- with the countries shuffled through it. Neither is wrong; they answer different asks.
SELECT "CompanyName", "Country"
FROM "Customers"
ORDER BY "Country", "CompanyName"
LIMIT 8;
