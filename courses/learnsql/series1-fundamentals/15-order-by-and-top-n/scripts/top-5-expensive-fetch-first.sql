-- Series 1 · Episode 15 · the SAME five rows, in the standard spelling.
-- LIMIT is what almost everyone writes, and PostgreSQL, MySQL, DuckDB and SQLite all
-- take it. FETCH FIRST n ROWS ONLY is the one in the SQL standard, and it is what
-- Oracle, IBM Db2 and SQL Server want. Write LIMIT; recognise this when you meet it in
-- somebody else's query. Series 4 · 05 is where the difference starts to cost you.
SELECT "ProductName", "UnitPrice"
FROM "Products"
ORDER BY "UnitPrice" DESC
FETCH FIRST 5 ROWS ONLY;
