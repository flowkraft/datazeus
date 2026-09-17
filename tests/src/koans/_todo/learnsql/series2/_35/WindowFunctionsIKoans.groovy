package datazeus.learnsql.series2._35

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 35
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Window Functions I: OVER, PARTITION BY & ROW_NUMBER — The Latest Row per Customer
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series2/_35/ when it is real.
 *
 *     zeus.bat koans learnsql series2 _50     (Windows)
 *     ./zeus.sh koans learnsql series2 _50    (macOS/Linux)
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
 * GOAL: see the detail row and its group's total at the same time, and rank within a group.
 * SQL: OVER (), PARTITION BY, ORDER BY inside OVER, ROW_NUMBER, RANK, DENSE_RANK.
 * MOVED DOWN FROM SERIES 3 in the 2026-08-24 pass. "Answer real business questions" is not true
 * without windows: rank-within-region and latest-row-per-customer ARE business questions, and
 * they are table stakes in an analyst screen. They sit mid-series (35, moved from 50 on
 * 2026-09-15), after the CTEs from episode 20: a window function reads badly without them.
 * ROW_NUMBER IS NAMED IN THE TITLE ON PURPOSE: "the latest row per customer" is the single most
 * searched SQL task there is, and the keywords-lead title convention exists so the person
 * searching for it finds this line.
 *
 * ── KOANS AND THE SERIES CASE (.docs/plan-academy-course-stories-artefacts.md §1, §3.2) ─────────────
 * The LESSON's case file: each customer's oldest open order and the top 10 on delivered sales; its
 * tie example: Series 1 · 15's two products at 12.5 (ROW_NUMBER <= 4 vs RANK <= 4). The KOANS: the dearest
 * product per category, the latest order per employee, top 3 products per category by units sold, a row beside
 * its category's average price, removing duplicates from a VALUES list with ROW_NUMBER. Measure first.
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * LEVEL ●●●● of ●●●●. BUILDS ON: Series 2 · 20 (keeping only ROW_NUMBER = 1 needs a CTE),
 * Series 1 · 30 (PARTITION BY groups the rows without collapsing them).
 * SETS UP: Series 2 · 45, 2 · 58.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class WindowFunctionsIKoans extends KoanBase {

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
