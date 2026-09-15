package datazeus.learnsql.series3._15

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 3 · 15
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Cohorts & Repeat-Purchase Analysis — Measuring Who Comes Back, and When
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series3/_15/ when it is real.
 *
 *     zeus.bat koans learnsql series3 _15     (Windows)
 *     ./zeus.sh koans learnsql series3 _15    (macOS/Linux)
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
 * GOAL: answer "who comes back?" — the analysis everything so far was for.
 * SQL: nothing new; CTEs, windows, date_trunc and joins doing real work together.
 * The one episode in Series 3 that is an ANALYSIS PATTERN rather than a SQL feature, and the
 * strongest "so this is what it was all for" moment in the course. Keep it that way.
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * LEVEL ●●●● of ●●●● — closes Block A. BUILDS ON: Series 2 · 10 (date_trunc, and the date spine for
 * months nobody came back in), 2 · 15 (cohort size at CUSTOMER grain), 2 · 20 (CTEs), 2 · 35 and
 * 2 · 45 (first order per customer, months since it), Series 3 · 00 (the grid is a pivot).
 * DATA: 25 customers over 19 months makes a sparse grid — decide whether it needs the bulk data.
 * Was 10 until the 2026-09-14 reorder.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class CohortsAndRepeatPurchaseKoans extends KoanBase {

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
