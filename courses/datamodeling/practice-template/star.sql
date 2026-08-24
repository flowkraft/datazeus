-- ============================================================================
--  YOUR STAR SCHEMA
--  Data Modeling · Series 3 · grown from episode 15 onward, finished at episode 50.
-- ============================================================================
--
--  The Series 3 counterpart to schema.sql. Same rules: `zeus practice reset` copies this in
--  once and never overwrites it, it rebuilds from scratch every run, and the data decides
--  whether you got it right.
--
--  WHAT CHANGES HERE
--  Series 1 asked "what should ONE ROW of this table be?" and you answered it for tables
--  that already existed. Series 3 asks the same question about a table nobody has built yet,
--  and the answer is the hardest decision in dimensional modelling: the GRAIN of the fact.
--  Get it wrong and every number computed from it is wrong in a way that looks plausible.
--
--  HOW YOU KNOW YOU GOT IT RIGHT — RECONCILIATION
--  Not "does it look like a star". The test is arithmetic, and it is the same test real
--  warehouse teams run every night:
--
--      the total in your fact table  ==  the total in the OLTP source
--
--  If those two numbers differ, you have double-counted (a join fanned out), dropped rows
--  (an inner join where a left join belonged), or built at the wrong grain. The shipped
--  main.fact_sales and main.dim_* are the reference answer — compare against them only
--  AFTER your own reconciliation passes, or you are copying rather than modelling.
--
--  This is also exactly what dbt's `unique` and `not_null` tests do, so what you write here
--  is the thing the Analytics Engineering track automates later.
--
--  Run it:   zeus practice run star
--  ...or open practice/northwind-practice.duckdb in CloudBeaver and run it there.
-- ============================================================================

DROP SCHEMA IF EXISTS star CASCADE;
CREATE SCHEMA star;


-- ── Episode 15 · Slowly Changing Dimensions ─────────────────────────────────
-- Decide Type 1, 2 or 3 per dimension BEFORE you build it — it is a structural choice, not
-- a loading detail. Type 2 needs valid_from / valid_to and a surrogate key that is NOT the
-- source system's id.


-- ── Episode 20 · Fact Grain & Additivity ────────────────────────────────────
-- Say the grain out loud, in one sentence, and write it here as a comment before any DDL:
--
--     One row of star.fact_sales is _______________________________.
--
-- Then enforce it: a PRIMARY KEY on the grain columns makes a wrong grain impossible to
-- load, the same way it did in Series 1.


-- ── Episode 25 · The Date Dimension ─────────────────────────────────────────
-- A real calendar table, not date_trunc: fiscal periods, holidays and week-numbering rules
-- that no date function knows about. Generate the rows; do not type them.


-- ── Episode 30 · Build It ───────────────────────────────────────────────────
-- Load the dimensions first, then the fact — the fact's foreign keys need something to point
-- at. Look at it in the ER Diagram tab when it is done; seeing the star is half the lesson.


-- ── Episode 50 · Project · Reconcile ────────────────────────────────────────
-- The two numbers must match. Write the check, run it, and keep it — a warehouse without a
-- reconciliation query is a warehouse nobody should trust.
--
-- SELECT (SELECT sum("UnitPrice" * "Quantity" * (1 - "Discount")) FROM main."Order Details")
--          AS oltp_total,
--        (SELECT sum(amount) FROM star.fact_sales) AS star_total;
