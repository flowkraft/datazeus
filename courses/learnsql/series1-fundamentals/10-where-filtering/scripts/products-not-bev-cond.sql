-- Series 1 · Episode 10 · NOT flips a whole test — parenthesize what it negates.
-- Everything EXCEPT beverages and condiments: 14 of the 20 products.
SELECT count(*)
FROM "Products"
WHERE NOT ("CategoryID" = 1 OR "CategoryID" = 2);
