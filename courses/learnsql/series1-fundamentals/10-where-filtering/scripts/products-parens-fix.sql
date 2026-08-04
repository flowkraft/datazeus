-- Series 1 · Episode 10 · the fix: parentheses decide the order yourself.
-- "(beverages OR condiments) AND under 15" — now only two rows pass.
SELECT "ProductName", "UnitPrice"
FROM "Products"
WHERE ("CategoryID" = 1 OR "CategoryID" = 2)
  AND "UnitPrice" < 15;
