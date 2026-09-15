-- ============================================================================
--  THE REFERENCE SOLUTION — Data Modeling · Series 1 · practice/schema.sql
--  ONE model that passes NorthwindModelChecks, grown episode by episode like the learner's.
-- ============================================================================
--
--  NOT AN ANSWER KEY. Any model that passes the checks is right; this file exists so the verify
--  gate can prove every check CAN pass, on DuckDB and on PostgreSQL, before a learner meets it.
--  The episode specs apply it with `practice` renamed to their own schema (dm_s1_NN) and run the
--  checks file against it — see StartHereSpec.
--
--  RULES OF THE FILE: portable SQL only; source tables UNQUALIFIED ("Shippers" resolves to main on
--  DuckDB and public on PostgreSQL); every load has an ORDER BY; no ";" inside a comment.
-- ============================================================================

DROP SCHEMA IF EXISTS practice CASCADE;
CREATE SCHEMA practice;


-- ── Episode 00 · Start Here: The Modeling Loop ──────────────────────────────
-- The lesson's table. Every product belongs to exactly one category.
CREATE TABLE practice."Categories" (
  "CategoryID"   INTEGER PRIMARY KEY,
  "CategoryName" VARCHAR NOT NULL,
  "Description"  VARCHAR
);
INSERT INTO practice."Categories"
SELECT "CategoryID", "CategoryName", "Description"
FROM "Categories"
ORDER BY "CategoryID";

-- The learner's turn. A number no two shippers share, a name every shipper has.
CREATE TABLE practice."Shippers" (
  "ShipperID"   INTEGER PRIMARY KEY,
  "CompanyName" VARCHAR NOT NULL,
  "Phone"       VARCHAR
);
INSERT INTO practice."Shippers"
SELECT "ShipperID", "CompanyName", "Phone"
FROM "Shippers"
ORDER BY "ShipperID";


-- ── Episode 15 · Grain & Keys ───────────────────────────────────────────────
-- One row per customer: the code is the key. The name is unique today too, but names change.
CREATE TABLE practice."Customers" (
  "CustomerID"   VARCHAR PRIMARY KEY,
  "CompanyName"  VARCHAR NOT NULL,
  "ContactName"  VARCHAR,
  "ContactTitle" VARCHAR,
  "City"         VARCHAR,
  "Country"      VARCHAR
);
INSERT INTO practice."Customers"
SELECT "CustomerID", "CompanyName", "ContactName", "ContactTitle", "City", "Country"
FROM "Customers"
ORDER BY "CustomerID";

-- One row per order.
CREATE TABLE practice."Orders" (
  "OrderID"     INTEGER PRIMARY KEY,
  "CustomerID"  VARCHAR,
  "EmployeeID"  INTEGER,
  "OrderDate"   TIMESTAMP,
  "ShippedDate" TIMESTAMP,
  "ShipVia"     INTEGER,
  "Freight"     DECIMAL(19,4)
);
INSERT INTO practice."Orders"
SELECT "OrderID", "CustomerID", "EmployeeID", "OrderDate", "ShippedDate", "ShipVia", "Freight"
FROM "Orders"
ORDER BY "OrderID";

-- One row per product.
CREATE TABLE practice."Products" (
  "ProductID"       INTEGER PRIMARY KEY,
  "ProductName"     VARCHAR NOT NULL,
  "SupplierID"      INTEGER,
  "CategoryID"      INTEGER,
  "QuantityPerUnit" VARCHAR,
  "UnitPrice"       DECIMAL(19,4)
);
INSERT INTO practice."Products"
SELECT "ProductID", "ProductName", "SupplierID", "CategoryID", "QuantityPerUnit", "UnitPrice"
FROM "Products"
ORDER BY "ProductID";

-- One row per supplier.
CREATE TABLE practice."Suppliers" (
  "SupplierID"   INTEGER PRIMARY KEY,
  "CompanyName"  VARCHAR NOT NULL,
  "ContactName"  VARCHAR,
  "ContactTitle" VARCHAR,
  "Country"      VARCHAR
);
INSERT INTO practice."Suppliers"
SELECT "SupplierID", "CompanyName", "ContactName", "ContactTitle", "Country"
FROM "Suppliers"
ORDER BY "SupplierID";

-- One row per employee.
CREATE TABLE practice."Employees" (
  "EmployeeID" INTEGER PRIMARY KEY,
  "LastName"   VARCHAR NOT NULL,
  "FirstName"  VARCHAR NOT NULL,
  "Title"      VARCHAR,
  "ReportsTo"  INTEGER
);
INSERT INTO practice."Employees"
SELECT "EmployeeID", "LastName", "FirstName", "Title", "ReportsTo"
FROM "Employees"
ORDER BY "EmployeeID";

-- One row per product on an order: the key is the pair.
CREATE TABLE practice."Order Details" (
  "OrderID"   INTEGER,
  "ProductID" INTEGER,
  "UnitPrice" DECIMAL(19,4),
  "Quantity"  SMALLINT,
  "Discount"  DECIMAL(8,4),
  PRIMARY KEY ("OrderID", "ProductID")
);
INSERT INTO practice."Order Details"
SELECT "OrderID", "ProductID", "UnitPrice", "Quantity", "Discount"
FROM "Order Details"
ORDER BY "OrderID", "ProductID";


-- ── Episode 20 · Many-to-Many & Junction Tables ─────────────────────────────
-- A product can come from several suppliers, each at its own price: the link is a table keyed by the
-- pair, and the price lives on the pair. Seeded with today's supplier of every product, at today's
-- unit price, until the buyer supplies the real supplier prices.
CREATE TABLE practice."Product Suppliers" (
  "ProductID"     INTEGER,
  "SupplierID"    INTEGER,
  "SupplierPrice" DECIMAL(19,4),
  PRIMARY KEY ("ProductID", "SupplierID")
);
INSERT INTO practice."Product Suppliers"
SELECT "ProductID", "SupplierID", "UnitPrice"
FROM "Products"
ORDER BY "ProductID";
