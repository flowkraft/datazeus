package datazeus.learnsql.series4._00

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 4 · 00
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * ANSI SQL vs Dialects — Why SQL That Works Here Fails on Another Database
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series4/_00/ when it is real.
 *
 *     zeus.bat koans learnsql series4 _00     (Windows)
 *     ./zeus.sh koans learnsql series4 _00    (macOS/Linux)
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
 * GOAL: understand why SQL that works here breaks there.
 * SQL: the standard versus what vendors implement, and the categories that differ (paging,
 * strings, dates, upsert, identifiers, quoting).
 * SERIES 4 EXISTS BECAUSE OF WHO THIS IS FOR: consultants, ISVs and tool vendors, not the analyst
 * who wants an answer. It was split out of Series 3 (where it was episodes 50-60) because nobody
 * was going to reach it there — and because it is the most differentiated content in the course.
 * DataPallas runs Northwind on five engines, so this is the house specialty, not a survey.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class AnsiSqlVsDialectsKoans extends KoanBase {

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
