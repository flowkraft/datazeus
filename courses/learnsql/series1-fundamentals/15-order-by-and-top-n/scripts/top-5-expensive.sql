-- Series 1 · Episode 15 · sort, then cut. This is the shape of every "top N" request
-- you will ever be handed: ORDER BY decides WHICH rows, LIMIT decides HOW MANY.
-- Thuringer Rostbratwurst 123.7900, Mishi Kobe Niku 97.0000, Gnocchi di nonna Alice
-- 38.0000, Camembert Pierrot 34.0000, Ikura 31.0000.
SELECT "ProductName", "UnitPrice"
FROM "Products"
ORDER BY "UnitPrice" DESC
LIMIT 5;
