package datazeus.learnsql.series2._50

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 50
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Window Functions I: OVER, PARTITION BY & ROW_NUMBER — The Latest Row per Customer
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series2/_50/ when it is real.
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
 * they are table stakes in an analyst screen. They close this series rather than opening it
 * because a window function reads badly without the CTEs from episode 08.
 * ROW_NUMBER IS NAMED IN THE TITLE ON PURPOSE: "the latest row per customer" is the single most
 * searched SQL task there is, and the keywords-lead title convention exists so the person
 * searching for it finds this line.
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
