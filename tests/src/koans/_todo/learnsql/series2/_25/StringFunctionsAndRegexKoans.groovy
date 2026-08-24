package datazeus.learnsql.series2._25

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 25
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * String Functions, LIKE & Regex — Clean Up Messy Real-World Data
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series2/_25/ when it is real.
 *
 *     zeus.bat koans learnsql series2 _25     (Windows)
 *     ./zeus.sh koans learnsql series2 _25    (macOS/Linux)
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
 * GOAL: work with text that nobody cleaned.
 * SQL: LIKE, ILIKE, wildcards and escaping, regex matching, TRIM, UPPER/LOWER, SUBSTRING,
 * SPLIT_PART, CONCAT, plus the number side: ROUND, CAST, formatting for a report.
 * MERGED FROM TWO EPISODES in the 2026-08-24 pass (old 25 "LIKE/ILIKE/Regex" and 30
 * "String & Number Functions"). Both were reference material a working person looks up rather
 * than memorises, and two episodes for one lookup table was not earning its place.
 * Ships with a cheat sheet — that is the honest format for this content.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class StringFunctionsAndRegexKoans extends KoanBase {

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
