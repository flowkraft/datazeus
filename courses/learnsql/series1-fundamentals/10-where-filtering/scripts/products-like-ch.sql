-- Series 1 · Episode 10 · LIKE matches text patterns; % stands for "anything here".
-- 'Ch%' = starts with Ch. Three products. (Case-exact, like every text comparison.)
SELECT "ProductName"
FROM "Products"
WHERE "ProductName" LIKE 'Ch%';
