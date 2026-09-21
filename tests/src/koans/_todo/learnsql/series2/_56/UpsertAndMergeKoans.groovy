package datazeus.learnsql.series2._56

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 56
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * UPSERT & MERGE — Insert or Update in One Statement, and Loads You Can Run Twice
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series2/_56/ when it is real.
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
 * GOAL: insert-or-update in one statement, and an insert you can run twice.
 * SQL: INSERT … ON CONFLICT DO NOTHING, ON CONFLICT (key) DO UPDATE SET … = EXCLUDED.…, MERGE.
 * Split from 55 on 2026-09-19.
 *
 * ── THE KOANS ───────────────────────────────────────────────────────────
 * On the throwaway copy KoanBase opens: a supplier's price list against "Products" — re-run an INSERT with
 * DO NOTHING and predict the row count; DO UPDATE the prices that changed; the same upsert as MERGE (MERGE INTO needs
 * DuckDB 1.4+: upgrade tests/pom.xml's duckdb.version from 1.1.3 to 1.4.4.0 first).
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * LEVEL ●● of ●●●●. BUILDS ON: Series 2 · 55 (INSERT … SELECT, transactions), 2 · 22 (CREATE TABLE AS).
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class UpsertAndMergeKoans extends KoanBase {

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
