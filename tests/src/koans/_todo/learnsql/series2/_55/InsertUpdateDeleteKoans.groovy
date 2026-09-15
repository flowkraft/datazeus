package datazeus.learnsql.series2._55

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 55
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * INSERT, UPDATE, DELETE, UPSERT & Transactions — Change the Data, Safely
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series2/_55/ when it is real.
 *
 *     zeus.bat koans learnsql series2 _48     (Windows)
 *     ./zeus.sh koans learnsql series2 _48    (macOS/Linux)
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
 * GOAL: change data on purpose, and be able to undo it.
 * SQL: INSERT (single, multi-row, INSERT … SELECT), UPDATE … WHERE, DELETE … WHERE, BEGIN,
 * COMMIT, ROLLBACK.
 * PLACED LATE ON PURPOSE, and this is the reason: the failure mode of teaching UPDATE early is a
 * learner who runs it without a WHERE. By here, filtering is second nature.
 * Teach the SELECT-first habit: write the WHERE as a SELECT, look at the rows, then change the
 * verb. BOUNDARY: isolation levels and locking are Data Ops; COMMIT/ROLLBACK basics are here.
 *
 * ── KOANS AND THE SERIES CASE (.docs/plan-sql-series2-story.md §0, §3) ─────────────
 * The LESSON's main example applies (supposed) courier confirmations to "Orders" inside a transaction and rolls
 * back. The KOANS run on the throwaway copy KoanBase opens, and on OTHER tables — a price change on
 * "Products" with the SELECT-first habit, INSERT … SELECT, a DELETE with the right WHERE, ON CONFLICT DO NOTHING
 * re-running an insert, DO UPDATE as an upsert, and the UPDATE-without-WHERE diagnose koan. Standalone after
 * Series 1.
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * LEVEL ●● of ●●●●. Late on purpose (see above). Renumbered from 40 on 2026-09-14 and from 48 on 2026-09-15 — Learn Data
 * Modeling's prerequisites ask for "INSERT from Series 2 · 55".
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class InsertUpdateDeleteKoans extends KoanBase {

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
