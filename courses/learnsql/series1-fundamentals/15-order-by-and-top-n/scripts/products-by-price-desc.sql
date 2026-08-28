-- Series 1 · Episode 15 · DESC — biggest first, which is what almost every real
-- request means. Thuringer Rostbratwurst at 123.7900, then Mishi Kobe Niku at
-- 97.0000, and then it drops to 38.0000: two premium lines, and everything else.
SELECT "ProductName", "UnitPrice"
FROM "Products"
ORDER BY "UnitPrice" DESC;
