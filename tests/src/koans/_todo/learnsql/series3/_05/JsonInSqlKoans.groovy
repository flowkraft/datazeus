package datazeus.learnsql.series3._05

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 3 · 05
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * JSON in SQL — Query Semi-Structured Data Without Leaving the Database
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series3/_05/ when it is real.
 *
 *     zeus.bat koans learnsql series3 _05     (Windows)
 *     ./zeus.sh koans learnsql series3 _05    (macOS/Linux)
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
 * GOAL: query semi-structured data without leaving the database.
 * SQL: JSON/JSONB columns, -> and ->>, path expressions, indexing a JSON field, and where the
 * vendors diverge.
 * BOUNDARY: Data Modeling Series 2 · 42 owns the DECISION to store JSON at all. This owns
 * querying it once that decision is made. Same word, two questions.
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * LEVEL ●● of ●●●●. BUILDS ON: Series 1 · 07 (types — JSON is text until it is parsed),
 * 1 · 45 (a missing key is a NULL), Series 2 · 30 (text functions).
 * DATA: Northwind has no JSON column — curriculum.yaml's bulk-dataset note says what to decide.
 * Was 15 until the 2026-09-14 reorder.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class JsonInSqlKoans extends KoanBase {

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
