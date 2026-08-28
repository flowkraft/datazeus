-- Series 1 · Episode 15 · THE TRAP, and the reason this lesson exists.
-- Asked for "the five most expensive products" and written in a hurry. It returns five
-- products with five real prices, no error, no warning — and it is not a top five:
--
--   Chai                         18.0000
--   Chang                        19.0000
--   Aniseed Syrup                10.0000   <- the THIRD CHEAPEST product in the catalogue
--   Chef Antons Cajun Seasoning  22.0000
--   Scottish Longbreads          12.5000
--
-- The most expensive product we sell — Thuringer Rostbratwurst at 123.7900 — is not on
-- it. LIMIT without ORDER BY is not a top five; it is five rows, and which five is not
-- promised. Compare with top-5-expensive.sql, which asks the question properly.
SELECT "ProductName", "UnitPrice"
FROM "Products"
LIMIT 5;
