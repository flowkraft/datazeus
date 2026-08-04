-- Series 1 · Episode 10 · the date habit: half-open ranges, >= the first day
-- AND < the first day of the NEXT month. Works for dates, timestamps, any month length.
-- This is the episode 00 query — now you can read every character of it.
SELECT count(*)
FROM "Orders"
WHERE "OrderDate" >= DATE '2024-06-01'
  AND "OrderDate" <  DATE '2024-07-01';
