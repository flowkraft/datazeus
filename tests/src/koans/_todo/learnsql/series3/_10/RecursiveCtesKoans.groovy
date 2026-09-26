package datazeus.learnsql.series3._10

import datazeus._internal.NorthwindCoKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 3 · 10
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Recursive CTEs — Walk an Org Chart to the Top
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series3/_10/ when it is real.
 *
 *     zeus.bat koans learnsql series3 _10     (Windows)
 *     ./zeus.sh koans learnsql series3 _10    (macOS/Linux)
 *
 * ── READ THESE FIRST ────────────────────────────────────────────────────
 *   NorthwindCoKoanBase                       shouldReturn, the ___ blank, Northwind Company S
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
 * GOAL: walk a hierarchy of unknown depth.
 * SQL: WITH RECURSIVE, the anchor and recursive terms, UNION ALL, a depth guard.
 * DATA: Northwind Company S ("Employees", 29 rows) — Northwind's 3-row Employees cannot
 * demonstrate depth, and S's org chart was deepened for this episode (curriculum.yaml, DEPTH):
 *   - 1 Maya Bauer, the CEO, "ReportsTo" NULL: the anchor.
 *   - sales stops at level 3 (managers 2 and 3, reps 4 to 12); finance at level 4 (14 → 17 →
 *     21 and 22); operations at level 6 (13 → 15 → 18 → 23 → 25 and 26; 15 → 19 → 24 → 27;
 *     13 → 16 → 20). One depth per branch, so no fixed number of self-joins is right.
 *   - from a warehouse operative (25, 26 or 27) the walk UP to Maya is five steps.
 *   - 28 and 29, interim sales managers, report to each other: a walk DOWN from Maya never
 *     meets them (27 rows), a walk UP from either never ends without the depth guard.
 * Always show the termination condition. A recursive CTE without one is an outage.
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * LEVEL ●●● of ●●●●. BUILDS ON: Series 2 · 20 (WITH), 2 · 30 (the self-join it repeats level
 * after level), 2 · 48 (UNION ALL joins the anchor to the recursive term).
 * ALSO: it builds Series 2 · 10's date spine on engines without generate_series — say so.
 * Was 00 until the 2026-09-14 reorder.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class RecursiveCtesKoans extends NorthwindCoKoanBase {

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
