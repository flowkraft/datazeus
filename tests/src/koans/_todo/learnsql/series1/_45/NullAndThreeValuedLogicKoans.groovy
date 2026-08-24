package datazeus.learnsql.series1._45

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 45
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * NULL & Three-Valued Logic — How Missing Values Change Your Results
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series1/_45/ when it is real.
 *
 *     zeus.bat koans learnsql series1 _45     (Windows)
 *     ./zeus.sh koans learnsql series1 _45    (macOS/Linux)
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
 * GOAL: stop NULL silently changing your answers.
 * SQL: IS NULL / IS NOT NULL, why = NULL never matches, NULL in AND/OR, COALESCE, NULLIF.
 * POSITION IS THE POINT: the reader has JUST created NULLs with a LEFT JOIN in episode 40, so
 * "WHERE col = NULL returns nothing" is demonstrable on data they made themselves. Taught early
 * beside the data types it is abstract trivia; taught here it is a bug they just produced.
 * MUST CLOSE THE LOOP BACK TO EPISODE 25, which taught aggregates twenty episodes ago and
 * therefore taught them NULL-unaware: COUNT(col) skips NULLs, COUNT(*) does not, and AVG divides
 * by the non-NULL count. That is a wrong-number-in-a-report bug, not a curiosity.
 * BOUNDARY: Data Modeling Series 1 · 37 owns nullable-or-not as a DESIGN decision. Same word,
 * different question. Name it.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class NullAndThreeValuedLogicKoans extends KoanBase {

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
