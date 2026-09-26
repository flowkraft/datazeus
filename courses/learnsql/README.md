# Learn SQL — SQL by Doing

Hands-on SQL on **Northwind** — the classic sales database, and a bigger generated version of the
same company once the questions outgrow 79 orders. You write on **PostgreSQL**; every figure in
every lesson is re-checked on **PostgreSQL and DuckDB** before it is published, and the vendor
differences are flagged where they bite.

**The promise:** you *write* the queries yourself, against real business data — guess, run,
understand. Not slides you watch.

## What's covered

Four series, 43 episodes, a natural progression. This is early — lessons are still being built
out, so the outline below is the direction, not a fixed contract. The ordered, authoritative list
is [curriculum.yaml](curriculum.yaml), which the course page, the roadmap card in every video and
the lesson folder names all read; this file is prose about it, and TocSpec keeps the two in step.

**Series 1 — SQL Fundamentals** (12 episodes). One table, then two: SELECT and column lists, data
types, WHERE, ORDER BY and FETCH FIRST, aliases, expressions and DISTINCT, the aggregates, GROUP
BY, HAVING, JOINs, and NULL's three-valued logic — ending in your first real report.

**Series 2 — Intermediate SQL** (17 episodes). The intermediate SQL people actually use at work:
subqueries and correlated subqueries, CASE, dates and times, multi-table JOINs and **grain**, CTEs,
views and temp tables, self-joins, **window functions** (two episodes), strings and regex, set
operations, GROUPING SETS / ROLLUP / CUBE, INSERT, UPDATE, DELETE and transactions, UPSERT and
MERGE, median and percentiles — ending in a project that answers one real business question end to
end. A case runs through it from time to time: a shipping-record outage that the ordered-sales
report hid.

**Series 3 — Advanced SQL** (11 episodes). The harder queries, and how to make a slow one fast:
PIVOT and UNPIVOT, JSON in SQL, recursive CTEs, gaps and islands, cohorts, writing a query that can
be fast, EXPLAIN / EXPLAIN ANALYZE, rewrites that change the plan, indexes and what they cost,
COUNT(DISTINCT), and tuning a slow query on a million orders.

**Series 4 — Cross-Vendor SQL** (3 episodes). One query, five databases: where ANSI SQL ends and
the dialects begin, portable paging, strings, dates and UPSERT, and Northwind installed on
PostgreSQL, Oracle, SQL Server, MySQL and Db2.

## The data

Series 1 runs on the frozen **Northwind** — 79 orders, small enough to check a result by eye.
Series 2 moves to **Northwind Company S** (10,000 orders), installed once from the Seed Data tab,
because the questions it asks need more rows than Northwind has. Series 3 adds **Northwind Company
L** (1,000,000 orders) wherever the lesson is about speed. Series 4 goes back to Northwind, because
there the point is the engine, not the rows.

## Not here, on purpose

Schema design — DDL, keys, constraints, normalization — is [Learn Data Modeling](../datamodeling/).
This course is about writing queries against a schema somebody has already designed. `excludes` in
[curriculum.yaml](curriculum.yaml) has the full list, and it renders on the course page under "Not
here, on purpose".
