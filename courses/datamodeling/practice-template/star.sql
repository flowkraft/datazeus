-- ============================================================================
--  YOUR STAR SCHEMAS
--  Data Modeling · Series 3 · built from episode 10 onward, finished at episode 60.
-- ============================================================================
--
--  The Series 3 counterpart to schema.sql. Same rules: `zeus practice reset` copies this in
--  once and never overwrites it, it rebuilds from scratch every run, and the data decides
--  whether you got it right.
--
--  WHAT CHANGES HERE
--  In Series 1 you modelled Northwind to RUN the business. Here you reshape that same model so
--  the business can REPORT on itself — and your source is your own Series 1 model, read through
--  the views your Series 1 project ended with.
--
--  HOW YOU KNOW YOU GOT IT RIGHT — RECONCILIATION
--  Not "does it look like a star". The test is arithmetic, the one real warehouse teams run
--  every night:
--
--      the total in your fact table  ==  the total in the source
--
--  If they differ you double-counted (a join fanned out), dropped rows, or built at the wrong
--  grain. Write the check the same day you write the table, and keep it.
--
--  main.fact_sales and main.dim_* are NOT an answer key. They are a separate star generated
--  for pivot-table volume, unrelated to Northwind's orders — Series 3 · 25 and · 30 judge them
--  as a star somebody else built.
--
--  Run it:   zeus practice run star
--  ...or open practice/northwind-practice.duckdb in CloudBeaver and run it there.
-- ============================================================================

-- ── PART 0 · THE SERIES 3 SEED — ours, not yours: leave this part exactly as it is ────────
-- Northwind ships ONE business process: orders. Three Series 3 lessons need data it does not
-- have, so it is generated here — deterministically, from the Northwind rows themselves, into
-- its own schema `seed`. main.* is never touched, so every Learn SQL number stays the same.
--
--   seed.stock_movements     a second business process (Series 3 · 20 and · 45): an opening
--                            balance, quarterly supplier receipts in packs of 6, and one
--                            stocktake at the end. Stock leaves when an order is PLACED. The
--                            balance never goes negative, and it ends exactly at
--                            main."Products"."UnitsInStock" — reconcile against that.
--   seed.sales_targets       budget vs actual (Series 3 · 45): one target per category per
--                            month, a different grain from the order lines on purpose.
--   seed.regions             territories an employee covers (Series 3 · 40). Several per
--   seed.territories         employee, one territory shared by two, and an allocation weight
--   seed.employee_territories per employee that sums to 1.
--
-- Re-runnable: it rebuilds the seed schema from nothing every time.

DROP SCHEMA IF EXISTS seed CASCADE;
CREATE SCHEMA seed;

CREATE TABLE seed.regions (
  region_id   INTEGER PRIMARY KEY,
  region_name VARCHAR NOT NULL
);
INSERT INTO seed.regions VALUES (1, 'Eastern'), (2, 'Western'), (3, 'Northern'), (4, 'Southern');

CREATE TABLE seed.territories (
  territory_id   VARCHAR PRIMARY KEY,
  territory_name VARCHAR NOT NULL,
  region_id      INTEGER NOT NULL REFERENCES seed.regions (region_id)
);
INSERT INTO seed.territories VALUES
  ('02116', 'Boston',       1), ('02139', 'Cambridge', 1), ('10019', 'New York', 1),
  ('98004', 'Bellevue',     2), ('98052', 'Redmond',   2), ('90405', 'Santa Monica', 2),
  ('48084', 'Troy',         3), ('75234', 'Dallas',    4);

CREATE TABLE seed.employee_territories (
  employee_id       INTEGER      NOT NULL,
  territory_id      VARCHAR      NOT NULL REFERENCES seed.territories (territory_id),
  allocation_weight DECIMAL(4,2) NOT NULL CHECK (allocation_weight > 0 AND allocation_weight <= 1),
  PRIMARY KEY (employee_id, territory_id)
);
INSERT INTO seed.employee_territories VALUES
  (1, '98004', 0.50), (1, '98052', 0.30), (1, '90405', 0.20),   -- Nancy: the West
  (2, '02116', 0.40), (2, '02139', 0.30), (2, '10019', 0.30),   -- Andrew: the East
  (3, '10019', 0.50), (3, '48084', 0.30), (3, '75234', 0.20);   -- Janet: New York is shared

CREATE TABLE seed.stock_movements AS
WITH sold_by_quarter AS (
  SELECT d."ProductID" AS product_id,
         CAST(date_trunc('quarter', o."OrderDate") AS DATE) AS quarter_start,
         sum(d."Quantity") AS qty
  FROM main."Order Details" d JOIN main."Orders" o USING ("OrderID")
  GROUP BY ALL
),
receipts AS (
  SELECT s.product_id, p."SupplierID" AS supplier_id, s.quarter_start AS movement_date,
         CAST(ceil(s.qty / 6.0) * 6 AS INTEGER) AS quantity
  FROM sold_by_quarter s JOIN main."Products" p ON p."ProductID" = s.product_id
),
per_product AS (
  SELECT p."ProductID" AS product_id, p."UnitsInStock" AS units_in_stock,
         greatest(p."UnitsInStock", 6) AS opening,
         coalesce((SELECT sum(d."Quantity") FROM main."Order Details" d WHERE d."ProductID" = p."ProductID"), 0) AS sold,
         coalesce((SELECT sum(r.quantity) FROM receipts r WHERE r.product_id = p."ProductID"), 0) AS received
  FROM main."Products" p
),
movements AS (
  SELECT product_id, NULL::INTEGER AS supplier_id, DATE '2022-10-01' AS movement_date,
         'opening' AS movement_type, opening AS quantity
  FROM per_product
  UNION ALL
  SELECT product_id, supplier_id, movement_date, 'receipt', quantity FROM receipts
  UNION ALL
  SELECT product_id, NULL, DATE '2024-06-30', 'stocktake',
         units_in_stock - (opening + received - sold)
  FROM per_product
  WHERE units_in_stock - (opening + received - sold) <> 0
)
SELECT CAST(row_number() OVER (ORDER BY movement_date, product_id, movement_type) AS INTEGER) AS movement_id,
       movement_date, product_id, supplier_id, movement_type, CAST(quantity AS INTEGER) AS quantity
FROM movements;

CREATE TABLE seed.sales_targets AS
WITH months AS (
  SELECT CAST(m AS DATE) AS month_start,
         CAST(date_diff('month', DATE '2022-12-01', m) AS INTEGER) AS month_index
  FROM range(DATE '2022-12-01', DATE '2024-07-01', INTERVAL 1 MONTH) t(m)
),
actual AS (
  SELECT p."CategoryID" AS category_id, CAST(date_trunc('month', o."OrderDate") AS DATE) AS month_start,
         sum(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS revenue
  FROM main."Order Details" d
  JOIN main."Orders" o USING ("OrderID")
  JOIN main."Products" p USING ("ProductID")
  GROUP BY ALL
),
avg_month AS (
  SELECT c."CategoryID" AS category_id,
         coalesce((SELECT sum(revenue) FROM actual a WHERE a.category_id = c."CategoryID"), 0) / 19.0 AS avg_revenue
  FROM main."Categories" c
)
SELECT m.month_start, a.category_id,
       CAST(round(a.avg_revenue * (0.90 + ((m.month_index * 37 + a.category_id * 11) % 30) / 100.0) / 50) * 50
            AS DECIMAL(12,2)) AS target_amount
FROM months m CROSS JOIN avg_month a
ORDER BY m.month_start, a.category_id;


-- ── PART 1 · YOURS FROM HERE ON ──────────────────────────────────────────────────────────
DROP SCHEMA IF EXISTS star CASCADE;
CREATE SCHEMA star;


-- ── Episode 05 · Facts, Dimensions & the Four-Step Design ─────────────────────
-- Before any DDL, write the four steps as comments: the business process, the grain in one
-- sentence, the dimensions, the facts.
--
--     One row of star.fact_sales is _______________________________.


-- ── Episode 10 · The Star Schema ────────────────────────────────────────────
-- Dimensions first, then the fact — the fact's keys need something to point at. Load from
-- your Series 1 views, not from main.*. Then reconcile, right here:
--
-- SELECT (SELECT sum("UnitPrice" * "Quantity" * (1 - "Discount")) FROM main."Order Details")
--          AS source_total,              -- 58153.31
--        (SELECT sum(net_amount) FROM star.fact_sales) AS star_total;


-- ── Episode 15 · Fact Grain & Additivity ────────────────────────────────────
-- A PRIMARY KEY on the grain columns makes a wrong grain impossible to load. Store the parts
-- of a ratio (gross, discount amount), never only the ratio.


-- ── Episode 20 · Fact Table Types ───────────────────────────────────────────
-- The accumulating snapshot (one row per order: ordered, required, shipped) and the month-end
-- stock snapshot from seed.stock_movements. The stock snapshot's last month must equal
-- main."Products"."UnitsInStock". No NULL keys: point them at Unknown / Not Applicable rows.


-- ── Episode 25 · The Date Dimension & Role-Playing ──────────────────────────
-- Generate dim_date for the whole range; never type the rows. One table, joined as order date,
-- required date and ship date.


-- ── Episode 30 · Degenerate & Junk Dimensions ───────────────────────────────
-- The order number stays in the fact. The stray flags get one small dimension.


-- ── Episode 35 · Slowly Changing Dimensions ─────────────────────────────────
-- Decide Type 1, 2 or 3 per attribute BEFORE you build. Type 2 needs valid_from / valid_to
-- and a surrogate key that is not the source's id.


-- ── Episode 40 · Bridge Tables ───────────────────────────────────────────────
-- Revenue by territory through seed.employee_territories — with the allocation weight, or the
-- total will not reconcile.


-- ── Episode 45 · Conformed Dimensions & the Bus Matrix ──────────────────────
-- Orders, stock and seed.sales_targets on shared dimensions. Budget vs actual is one row per
-- category per month — a different grain, so a shrunken product dimension.


-- ── Episode 60 · Project ────────────────────────────────────────────────────
-- Every star, every reconciliation query, one run. Keep the checks: they are the dbt tests of
-- the Analytics Engineering track, written before you knew their name.
