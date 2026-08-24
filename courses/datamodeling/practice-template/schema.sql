-- ============================================================================
--  YOUR REBUILD OF NORTHWIND'S INTEGRITY LAYER
--  Data Modeling · Series 1 · grown from episode 10 onward, finished at episode 60.
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


-- ── Episode 10 · Grain & Primary Keys ───────────────────────────────────────
-- Start with the easy one, then do "Order Details" and let it argue with you.
--
-- Before you write anything: say out loud what ONE ROW of the table is. One row of
-- Categories is one category. One row of "Order Details" is... that is the whole lesson.
-- Try PRIMARY KEY ("OrderID") first. Read what the database tells you.

-- CREATE TABLE practice."Categories" (
--   "CategoryID"   INTEGER PRIMARY KEY,
--   "CategoryName" VARCHAR NOT NULL
-- );
-- INSERT INTO practice."Categories"
--   SELECT "CategoryID", "CategoryName" FROM main."Categories";


-- ── Episode 15 · Foreign Keys & Referential Integrity ───────────────────────
-- Declare the parent before the child; a FK cannot point at a table that is not there yet.
-- Northwind's data is referentially clean, so every correct FK will load fine — the payoff
-- is the NEXT bad row, not this one.
--
-- (Note: DuckDB does not support ON DELETE CASCADE. Postgres does. Worth knowing.)


-- ── Episode 20 · One-to-Many & Junction Tables ──────────────────────────────
-- "Order Details" is a junction that carries facts of its own. Give it the composite key
-- you worked out in episode 10.


-- ── Episode 30 · Data Types ─────────────────────────────────────────────────
-- Money is not a float. Look at what the Database Schema tab says the real columns are,
-- and decide whether you agree.


-- ── Episode 35 · Constraints ────────────────────────────────────────────────
-- NOT NULL, CHECK, UNIQUE, DEFAULT. Be careful with NOT NULL on "Region" — check the data
-- before you assume. And a CHECK that reality violates is a bug in your model, not in the
-- business.


-- ── Episode 60 · Project ────────────────────────────────────────────────────
-- Every table, every key, every constraint, one run, the full dataset. When this file runs
-- clean end to end, you have rebuilt a real database's integrity layer from the data up.
