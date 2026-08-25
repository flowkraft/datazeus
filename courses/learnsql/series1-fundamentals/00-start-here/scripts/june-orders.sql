-- Series 1 · Episode 00 · the hero query.
-- The boss asks: "How many orders did we get in June?"  You don't scroll — you ask.
-- GET, not SHIP: Northwind's "Orders" carries BOTH "OrderDate" and "ShippedDate", so
-- "shipped in June" is a different question with a different answer. This lesson teaches
-- translating a question into the RIGHT query, so the two have to agree. The article and
-- the video both say "get"; they said different things until 2026-08-25.
SELECT count(*)
FROM "Orders"
WHERE "OrderDate" >= DATE '2024-06-01'
  AND "OrderDate" <  DATE '2024-07-01';
