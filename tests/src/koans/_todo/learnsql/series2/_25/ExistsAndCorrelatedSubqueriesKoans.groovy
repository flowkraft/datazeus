package datazeus.learnsql.series2._25

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 25
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * EXISTS & Correlated Subqueries — Questions About Each Row, and the NOT IN Trap
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
 * GOAL: ask a question about each row — does it have a match, or has it never had one — and
 * get the "never" right.
 * SQL: EXISTS, NOT EXISTS, correlated against uncorrelated subqueries, and why EXISTS can stop at
 * the first match it finds.
 * SPLIT FROM THE OLD SUBQUERIES EPISODE on 2026-09-14. The uncorrelated half is Series 2 · 00; this
 * is the harder half, placed after CTEs so correlation is met with the tool that makes it readable.
 *
 * THE NOT IN TRAP, ON NORTHWIND'S OWN DATA (DuckDB, 2026-09-14 — re-assert on both engines).
 * "Which employees manage nobody?"
 *   WHERE "EmployeeID" NOT IN (SELECT "ReportsTo" FROM "Employees")    -> NO ROWS AT ALL
 *   WHERE NOT EXISTS (… x."ReportsTo" = e."EmployeeID")                 -> Janet, Nancy
 * Andrew reports to nobody, so one "ReportsTo" is NULL — and a single NULL in the list makes every
 * NOT IN test UNKNOWN. It is Series 1 · 45's three-valued logic detonating inside a construct that
 * looks nothing like a NULL comparison, and an empty result reads as "nothing to report" rather
 * than as a bug. Teach the habit: NOT EXISTS, always. Series 3 · 22 comes back to it as a query
 * plan; this episode owns the correctness.
 *
 * A SECOND VERIFIED QUESTION, which Series 2 · 35 answers again with EXCEPT: customers who ordered
 * in Q1 2024 and never since — 10 of them (EXISTS … AND NOT EXISTS …).
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * LEVEL ●●● of ●●●●. BUILDS ON: Series 2 · 00, 2 · 20, Series 1 · 35 (the HAVING promise),
 * 1 · 45 (UNKNOWN).
 * SETS UP: Series 2 · 35 (EXCEPT, the same question), 2 · 60 (the project's "not since" question),
 * Series 3 · 22 (NOT EXISTS as a plan).
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class ExistsAndCorrelatedSubqueriesKoans extends KoanBase {

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
