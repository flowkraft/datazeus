-- Series 1 · Episode 05 · LIMIT controls the ROWS, the select list the COLUMNS.
-- Products holds 20 rows; this asks for 5. UnitPrice prints as 18.0000 — that is its type.
SELECT "ProductName", "UnitPrice"
FROM "Products"
LIMIT 5;
