-- Series 1 · Episode 15 · sorting IS comparing, so episode 07 is not behind you.
-- "PostalCode" is TEXT (episode 07: the leading zero settles it), so it sorts like a
-- dictionary — character by character. Five rows, and the fourth is the whole point:
--
--   Leipzig    04179
--   Berlin     12209
--   Stuttgart  70563
--   Graz        8010   <- numerically the SMALLEST of the last three, filed in the middle
--   München    80805
--
-- '8' beats '7', so 8010 lands after 70563; then '8010' vs '80805' splits at the fourth
-- character, '1' before '8', so it lands before 80805. Nothing is broken. Sort a column
-- of AMOUNTS that arrived as text and the same rule puts your biggest number halfway
-- down the report — the bug episode 07 warned about, seen in a whole column at once.
SELECT "CompanyName", "City", "PostalCode"
FROM "Customers"
WHERE "City" IN ('Leipzig', 'Berlin', 'Stuttgart', 'Graz', 'München')
ORDER BY "PostalCode";
