-- Series 1 · Episode 05 · the database describing its own tables.
-- Note the two kinds of quotes: 'Products' is a VALUE, "ProductName" is a COLUMN.
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'Products'
ORDER BY ordinal_position;
