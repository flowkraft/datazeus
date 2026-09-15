package datazeus.learnsql.series2._30

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 30
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Self-JOINs & Hierarchies — Relating Rows to Other Rows in the Same Table
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series2/_30/ when it is real.
 *
 *     zeus.bat koans learnsql series2 _42     (Windows)
 *     ./zeus.sh koans learnsql series2 _42    (macOS/Linux)
 *
 * ── READ THESE FIRST ────────────────────────────────────────────────────
 *   KoanBase                                  shouldReturn, the ___ blank, the dataset
 *   learnsql/series1/_10/WhereFilteringKoans   the worked example: twelve koans, one per idea
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
 * GOAL: relate rows in one table to other rows in the same table.
 * SQL: joining a table to itself, aliasing both sides, the manager/employee shape.
 * Northwind's "Employees" is thin (3 rows) — say so rather than pretending. It has exactly enough
 * for the one idea (checked 2026-09-14): Nancy and Janet report to Andrew, and Andrew's
 * "ReportsTo" is NULL. So "each employee beside their manager" loses Andrew under a plain JOIN and
 * keeps him under a LEFT JOIN — Series 1 · 40's rule, on a table joined to itself. If the lesson
 * needs depth, use a richer example rather than inventing Northwind rows.
 * Recursive walking is Series 3 · 10.
 *
 * ── KOANS AND THE SERIES CASE (.docs/plan-sql-series2-story.md §0, §3) ─────────────
 * The case does not appear in this episode (NONE). The KOANS: each employee beside their manager (LEFT JOIN
 * keeps Andrew), customers sharing a city (a.id < b.id, not twice, not with themselves), suppliers sharing a
 * country, and — if the lesson takes .docs §5.6 — product pairs on the same order (Chai + Chang: 9 orders).
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * LEVEL ●● of ●●●●. A different join SHAPE — one table, two roles — and not a third episode about
 * rows that drop or multiply, which is why it no longer sits beside Series 2 · 15.
 * BUILDS ON: Series 1 · 40. SETS UP: Series 3 · 10, where a recursive CTE walks the same hierarchy.
 * Was 10 until the 2026-09-14 reorder.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class SelfJoinsAndHierarchiesKoans extends KoanBase {

    // TODO: koans, one per idea in the lesson, in the same order.
    //
    //   fragment koan — the lesson is the one blanked token
    //     def "keep only the German customers"() {
    //         expect:
    //         shouldReturn 11, '''
    //             SELECT count(*) FROM "Customers" WHERE "Country" = ___
    //         '''
    //     }
    //
    //   whole-query koan — write all of it; a row set cannot be guessed
    //     def "the five cheapest products, with their prices"() {
    //         expect:
    //         shouldReturn([["Geitost", 2.5000], /* … */], '''
    //             ___
    //         ''')
    //     }
}
