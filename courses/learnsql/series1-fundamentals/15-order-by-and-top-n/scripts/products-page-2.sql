-- Series 1 · Episode 15 · OFFSET — skip the rows you have already shown. This is
-- page two of a five-per-page price list: products 6 to 10, biggest first.
--
--   Uncle Bobs Organic Dried Pears  30.0000
--   Tofu                            23.2500
--   Chef Antons Cajun Seasoning     22.0000
--   Queso Cabrales                  21.0000
--   Ravioli Angelo                  19.5000
--
-- OFFSET only means anything on top of a STABLE order. Without an ORDER BY that
-- settles every tie, page two can repeat a row from page one and quietly drop another
-- — the same defect as the top-five trap, arriving on the second screen instead of
-- the first. Series 3 · 20 covers what OFFSET costs on a large table.
SELECT "ProductName", "UnitPrice"
FROM "Products"
ORDER BY "UnitPrice" DESC
LIMIT 5 OFFSET 5;
