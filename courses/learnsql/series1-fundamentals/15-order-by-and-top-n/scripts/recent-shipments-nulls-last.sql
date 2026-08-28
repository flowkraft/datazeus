-- Series 1 · Episode 15 · the same question, asked properly. Two words — NULLS LAST —
-- and every engine gives the same five orders:
--
--   6   AROUT   2024-06-13
--   4   ALFKI   2024-06-10
--   79  OTTIK   2024-05-30
--   3   BERGS   2024-05-25
--   78  MORGK   2024-05-22
--
-- NULLS FIRST is the other half of the pair, for when "not done yet" is the thing you
-- are looking for. Say which you want and the question stops depending on the engine.
SELECT "OrderID", "CustomerID", "ShippedDate"
FROM "Orders"
ORDER BY "ShippedDate" DESC NULLS LAST
LIMIT 5;
