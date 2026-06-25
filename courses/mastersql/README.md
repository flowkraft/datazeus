# Master SQL — Learn SQL by Doing

The first DataZeus track. Hands-on SQL on the real **Northwind** dataset, taught on
**PostgreSQL** with portable ANSI SQL (vendor differences flagged). Mirrors the website
at `content/datazeus-academy/master-sql/` (Contentlayer `topic: master-sql`).

**The promise:** you *write* the queries yourself, against real business data — you don't
watch slides. Each lesson is a self-contained bundle: `lesson.mdx` · `scripts/*.sql` ·
`cards/cards.yaml` · `video/index.tsx`. Its koans live in `../../tests/src/koans`
(package `datazeus.mastersql.series<N>.ep<NN>`), gated by a verified spec in `../../tests/src/verify`.
Run one lesson's koans with `./koans.sh sql <series> <episode>` (e.g. `./koans.sh sql S1 _00`).

## Series & episodes
Three series, natural progression. Episodes are **gap-numbered** (00, 05, 10, …) so new
lessons can be slotted in between without renumbering the rest.
> Status: scaffolding. `series1-fundamentals/00-write-your-first-query` is the reference
> lesson, wired end-to-end (mdx · scripts · cards · video · verify spec · koans).

### Series 1 — Fundamentals  (query one table, then glue two)
- [x] **00 · Write Your First Query** — `series1-fundamentals/00-write-your-first-query/` · slug `master-sql-series1-00-write-your-first-query`
- [ ] 05 · Ask Your Database Anything: SELECT
- [ ] 10 · Throw Away the Rows You Don't Care About: WHERE
- [ ] 15 · Who's Your #1 Customer? ORDER BY & Top-N
- [ ] 20 · DISTINCT, Aliases & Expressions
- [ ] 25 · One Number That Runs the Business: aggregates
- [ ] 30 · GROUP BY
- [ ] 35 · Stop Being Scared of JOINs
- [ ] 40 · NULL & three-valued logic
- [ ] 45 · Your First Real Report

### Series 2 — Intermediate  (answer real business questions)
- [ ] multi-table joins · HAVING · self-joins · subqueries · CASE · dates · text/regex · set ops · views

### Series 3 — Advanced  (production-grade + cross-vendor)
- [ ] window functions · CTEs · recursive CTEs · pivot · cohorts · EXPLAIN · indexes ·
      from-slow-to-fast · ANSI vs dialects · portable SQL · same-data-every-engine
      (the JOINs video already exists in cli-remotion as composition `0040`)

## Conventions
- Lesson folder: `<series>/NN-slug/`. The website strips the `NN-` for clean URLs.
- The lesson's `scripts/*.sql` are the literal queries the video + blog show, and the
  ones the verified spec runs — change the SQL in one place only.
- Verified spec (`tests/src/verify/.../<Lesson>Spec.groovy`) = the publish gate + the
  source of any "expected N" numbers shown on screen (here: `june_orders = 4`).
- Koans (`tests/src/koans/.../<Lesson>Koans.groovy`) = a separate, richer fill-in-the-`___`
  exercise on the same topic — practice, not a blanked copy of the gate.
