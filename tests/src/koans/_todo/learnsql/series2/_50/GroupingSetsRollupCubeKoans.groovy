package datazeus.learnsql.series2._50

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 50
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * GROUPING SETS, ROLLUP & CUBE — Subtotals and Grand Totals in One Pass
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series2/_50/ when it is real.
 *
 *     zeus.bat koans learnsql series2 _38     (Windows)
 *     ./zeus.sh koans learnsql series2 _38    (macOS/Linux)
 *
 * WHY THIS FILE EXISTS: written 2026-09-15 — the episode had a lesson brief and no koan brief.
 *
 * ── READ THESE FIRST ────────────────────────────────────────────────────
 *   KoanBase                                  shouldReturn, the ___ blank, the dataset
 *   learnsql/series1/_10/WhereFilteringKoans   the worked example: twelve koans, one per idea
 *   courses/learnsql/series2-intermediate/50-grouping-sets-rollup-cube/_todo-50-grouping-sets-rollup-cube.mdx   the lesson brief
 *
 * ── THE RULES ───────────────────────────────────────────────────────────
 *  1. THE BLANK GOES INSIDE THE SQL. The learner WRITES THE QUERY; the koan checks the
 *     RESULT. You learn SQL by writing queries, not by typing in a number.
 *  2. ONE KOAN PER IDEA IN THE LESSON, IN THE LESSON'S ORDER. The koans are a parallel set of
 *     drills, not a blanked copy of the gate in src/verify.
 *  3. PREDICT FIRST. Word each koan so the reader can say the answer out loud before running
 *     it — that is the skill, and the hint shows returned-vs-expected so they fix the SQL
 *     rather than guess a number.
 *  4. END WITH A WHOLE-QUERY KOAN. A row-set expectation is fake-resistant in a way a single
 *     count is not.
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: a report with group totals, subtotals and a grand total from one query, with the subtotal
 * rows labelled — ROLLUP and GROUPING SETS as the everyday tools, GROUPING() to tell a subtotal's
 * NULL from a real one, CUBE and GROUPING_ID in a line each.
 * HANDS-ON (curriculum.yaml): koan:equivalent, koan:predict — `equivalent` is the spine (the UNION ALL
 * version and the GROUPING SETS version return the same rows); `predict` on where the NULLs land.
 *
 * ── KOANS AND THE SERIES CASE (.docs/plan-sql-series2-story.md §0, §3) ─────────────
 * The LESSON's main example is the case: rep x courier with subtotals (Nancy–Speedy Express 25 orders /
 * 24 with no ShippedDate …); its NULL-collision example is "Customers"."Region". The KOANS use other
 * tables: units per category per order year with ROLLUP; supplier country x category with GROUPING SETS;
 * a GROUPING() + CASE label; predict which rows carry the subtotal NULL; the UNION ALL equivalence.
 * Measure every figure first, on both engines.
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * LEVEL ●●● of ●●●●. BUILDS ON: Series 2 · 48 (UNION ALL), 2 · 05 (the CASE label), Series 1 · 45 (NULL).
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class GroupingSetsRollupCubeKoans extends KoanBase {

    // TODO: koans, one per idea in the lesson, in the same order. See _42's brief for the
    // fragment-koan and whole-query-koan shapes.
}
