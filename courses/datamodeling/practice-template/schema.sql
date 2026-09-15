-- ============================================================================
--  YOUR MODEL OF NORTHWIND'S BUSINESS
--  Data Modeling · Series 1 · started at episode 00, grown from 15, finished at episode 60.
-- ============================================================================
--
--  `zeus practice reset` copies this file into practice/schema.sql the first time and
--  never overwrites it afterwards, so your work is safe from every future update.
--
--  WHAT THIS FILE IS FOR
--  The Northwind you have been reading declares NO primary keys and NO foreign keys. Its
--  relationships are completely real — an order really does belong to a customer — but
--  nothing in the database says so, and nothing stops a row that breaks it. This file is
--  you putting that back, one episode at a time.
--
--  HOW YOU KNOW YOU GOT IT RIGHT
--  There is no answer key. You load the real rows into your own tables, and they either
--  fit or they do not. Every way they fail to fit has a name:
--
--     rows refuse to load on a PRIMARY KEY   ->  you guessed the grain wrong
--     rows refuse to load on a NOT NULL      ->  that column is genuinely optional
--     rows refuse to load on a CHECK         ->  reality is wider than your rule
--     they all load                          ->  your model matches the business
--
--  WHY IT REBUILDS INSTEAD OF ALTERING
--  DuckDB can ALTER TABLE ADD PRIMARY KEY, but it cannot ADD FOREIGN KEY, ADD UNIQUE or
--  ADD CHECK. So integrity cannot be bolted onto Northwind in place — you declare the
--  constraints inline on a fresh table and copy the rows in. That is also why the whole
--  file is safe to re-run: it throws away everything it made last time and starts clean.
--
--  Run it whenever you like:   zeus practice run
--  ...or open practice/northwind-practice.duckdb in CloudBeaver and run it there.
--  The shipped northwind.duckdb is never touched by any of this.
-- ============================================================================

DROP SCHEMA IF EXISTS practice CASCADE;
CREATE SCHEMA practice;


-- ── Episode 00 · Start Here: The Modeling Loop ──────────────────────────────
-- Say it first, as a sentence: "every product belongs to exactly one category".
-- Then one table, one load, and try to get a bad row past it. The lesson's version, to copy:
--
-- CREATE TABLE practice."Categories" (
--   "CategoryID"   INTEGER PRIMARY KEY,
--   "CategoryName" VARCHAR NOT NULL,
--   "Description"  VARCHAR
-- );
-- INSERT INTO practice."Categories"
-- SELECT "CategoryID", "CategoryName", "Description"
-- FROM "Categories"
-- ORDER BY "CategoryID";
--
-- YOUR TURN — Shippers: a number no two shippers share, and a name every shipper has.
-- Write it below (not as a comment), load Northwind's 3 shippers, then run the checks:
--   zeus practice run
--   zeus koans datamodeling series1
--
-- CREATE TABLE practice."Shippers" (
--   "ShipperID"   INTEGER ___,
--   "CompanyName" VARCHAR ___,
--   "Phone"       VARCHAR
-- );


-- ── Episode 05 · Entities & Attributes ──────────────────────────────────────
-- No table to build yet. Open the ER Diagram tab on Northwind, list the THINGS you hear in the
-- business, and decide the contact: two columns on Customers, or a thing of its own. The reason
-- is the work — write it here, where your later lessons will read it back.
--
-- Episode 05 · the things I hear:
-- customer, order, product, ___
-- A contact is ___
--   because ___


-- ── Episode 15 · Grain & Keys ───────────────────────────────────────────────
-- Before you write anything: say out loud what ONE ROW of the table is. One row of Categories
-- is one category. One row of "Order Details" is... that is the whole lesson.
-- Try PRIMARY KEY ("OrderID") on the order lines first. Read what the database tells you,
-- then rebuild it keyed by the pair, as the lesson does:
--
-- CREATE TABLE practice."Order Details" (
--   "OrderID"   INTEGER,
--   "ProductID" INTEGER,
--   "UnitPrice" DECIMAL(19,4),
--   "Quantity"  SMALLINT,
--   "Discount"  DECIMAL(8,4),
--   PRIMARY KEY ("OrderID", "ProductID")
-- );
--
-- YOUR TURN — say the grain, then declare a key, for "Customers", "Orders", "Products",
-- "Suppliers" and "Employees" too. Load each from Northwind's real rows (ORDER BY the key),
-- then run the checks:
--   zeus practice run
--   zeus koans datamodeling series1


-- ── Episode 20 · Many-to-Many & Junction Tables ─────────────────────────────
-- The order line is a thing with facts of its own. And a product can now come from several
-- suppliers, each at its own price — Products.SupplierID cannot say that.
--
-- YOUR TURN — give the link a table: one row per product and supplier, the price on the pair.
-- Load today's supplier of every product (20 rows, today's unit price as the starting price).
-- Do NOT add extra suppliers here: the checks add a second supplier themselves and read it back.
--
-- CREATE TABLE practice."Product Suppliers" (
--   "ProductID"     INTEGER,
--   "SupplierID"    INTEGER,
--   "SupplierPrice" DECIMAL(19,4),
--   PRIMARY KEY (___)
-- );
--   zeus practice run
--   zeus koans datamodeling series1


-- ── Episode 25 · Normalization ──────────────────────────────────────────────
-- Build the flat table from your Learn SQL report query, find its anomalies, decompose it.


-- ── Episode 30 · Copy or Reference ──────────────────────────────────────────
-- Decide which copies are history (the price on the order line, the ship-to address) and keep
-- them. Change a price in YOUR tables and check what last year's orders still say.


-- ── Episode 35 · Supertypes & Subtypes ──────────────────────────────────────
-- Customers, suppliers, shippers, employees: record the choice you made, and why, as a comment.


-- ── Episode 40 · Data Types & Domains ───────────────────────────────────────
-- Money is not a float. Look at what the Database Schema tab says the real columns are, and
-- decide whether you agree.


-- ── Episode 45 · Foreign Keys ───────────────────────────────────────────────
-- Declare the parent before the child. Northwind's data is referentially clean, so every
-- correct FK loads — the payoff is the NEXT bad row. (DuckDB has no ON DELETE CASCADE.)


-- ── Episode 50 · Constraints ────────────────────────────────────────────────
-- NOT NULL, CHECK, UNIQUE, DEFAULT. Check the data before NOT NULL on "Region". A CHECK that
-- reality violates is a bug in your model, not in the business.


-- ── Episode 52 · What an Empty Cell Should Mean ─────────────────────────────
-- For every nullable column, one comment: optional value, fact not there yet, or a different
-- kind of thing?


-- ── Episode 55 · From Diagram to DDL, and Naming ────────────────────────────
-- Regenerate the diagram from this file and diff it against the one you drew.


-- ── Episode 60 · Project ────────────────────────────────────────────────────
-- Every table, every key, every rule, one run. End the file with the views the project names
-- (customers, orders, order lines, contacts, product suppliers): the checks read only those,
-- and Series 3 builds its stars from them.
