# Northwind (DuckDB)

The canonical dataset for DataZeus. Learn SQL reads it, Data Modeling rebuilds it, Data
Warehousing reshapes it. `northwind.duckdb` is opened as a **throwaway copy** by every koan
(see `KoanBase.setupSpec`), so nothing a learner does can reach this file.

---

## ⚠️ THIS DATABASE DECLARES NO CONSTRAINTS. THAT IS DELIBERATE.

```
SELECT * FROM information_schema.table_constraints;   -- 0 rows
```

No primary keys. No foreign keys. No unique constraints. **Do not add them.**

It looks like an export bug, and in origin it is one — the upstream JPA entities in
`documentburster.common.db.northwind` carry the whole relational model (`@IdClass` composite
PK on Order Details, `@ManyToOne`/`@JoinColumn` on 12 of the 13 entities, `hbm2ddl.auto=create`),
so the PostgreSQL Northwind has all of it and the DuckDB export flattened it away.

**Data Modeling Series 1 is built on that absence.** The entire series is the learner
rebuilding this database's integrity layer into their own `practice` schema, one episode at a
time, with the real rows as the grader. Add the constraints here and there is nothing left to
rebuild — you would delete thirteen episodes with one commit.

The relationships are real. Nothing enforces them. That gap *is* the course.

> If a track genuinely needs a constrained Northwind (predict-rung koans, for instance, would
> answer ACCEPTED against this file), generate a **second** artifact from the same JPA
> metadata, or point those koans at the PostgreSQL copy. Do not change this one.

## What the data is like, and what depends on it

Verified 2026-08-24 — these are load-bearing for specific lessons. Check here before
regenerating the dataset.

| Property | Value | What relies on it |
|---|---|---|
| Referential integrity | **clean** — 0 orphan order lines, orders→customers, products→suppliers | Every FK a learner declares in `schema.sql` loads successfully. The payoff is the *next* bad row, not a failure on existing ones |
| `"Order Details"` grain | `(OrderID, ProductID)`, 193 rows, no duplicates | `PRIMARY KEY("OrderID")` alone is **rejected on load** — that rejection is how Data Modeling S1/10 teaches grain, without an answer key |
| `Customers."Region"` | contains NULLs | `NOT NULL` on it is **rejected** — how S1/35 teaches optionality from data rather than from prose |
| Cheapest product | 4.50 | `CHECK("UnitPrice" > 10)` is **rejected** — teaches that a constraint reality violates is a bug in the model |
| `"Order Details"."UnitPrice"` | present, but **0 of 193 rows differ** from `Products."UnitPrice"` | S2/25 (temporal data). The price-history *structure* is here but inert. **Do not seed a difference** — the lesson is the learner running an `UPDATE` in their own practice schema and watching the historical line hold |
| `Employees` | **3 rows, 2 with a manager** | ⚠️ Too thin to teach hierarchies. The canonical Northwind has 9. Data Modeling S2/20 uses the library's category tree instead. Restore the full 9 if you ever regenerate, and the hierarchy options reopen |
| OLAP tables | `fact_sales`, `dim_*`, `vw_*` already present | Data Warehousing, and the Data Modeling S3 project's reconciliation check |

## Engine notes

Tested on DuckDB v1.5.4. The `tests/pom.xml` pins `duckdb_jdbc` **1.1.3** — re-check anything
below against that version before relying on it, or bump the pin.

**Enforced:** PRIMARY KEY (including composite) · UNIQUE · NOT NULL · CHECK (column and
table-level) · FOREIGN KEY (orphan inserts, deletes of referenced parents, self-referencing)
· DEFAULT · generated columns · ENUM types · `ALTER TABLE ADD PRIMARY KEY` ·
`ALTER COLUMN SET NOT NULL`. Multiple NULLs in a UNIQUE column are correctly allowed, and a
NULL FK correctly models an optional relationship.

**Not supported** — each one costs a specific lesson, so they are listed rather than
discovered:

| Missing | Consequence |
|---|---|
| `ALTER TABLE ADD FOREIGN KEY / UNIQUE / CHECK` | **Integrity cannot be bolted on in place.** This is why `practice/schema.sql` is a *rebuild* script with constraints inline, not a list of ALTERs |
| `ON DELETE CASCADE` / `SET NULL` | S1/15 can teach and test referential integrity fully, but cannot demonstrate referential *actions* |
| `CREATE DOMAIN` | S1/30 is titled "Data Types & **Domains**" — the DOMAIN half needs CloudBeaver/Postgres. `ENUM` works and covers much of the ground |
| Partial unique indexes (`UNIQUE … WHERE`) | the soft-delete pattern |
| `EXCLUDE USING gist` | overlapping-booking prevention |
| `DEFERRABLE` | circular FKs, bulk-load ordering |

Treat these as content, not as apologies. A house that ships against five engines should say
"this exists over there and not here" out loud — it is a portability lesson either way.
