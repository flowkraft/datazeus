package datazeus.learnsql.series2._06

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 06
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Join Fan-Out & Grain — When a Join Silently Multiplies Your Rows
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series2/_06/ when it is real.
 *
 *     zeus.bat koans learnsql series2 _06     (Windows)
 *     ./zeus.sh koans learnsql series2 _06    (macOS/Linux)
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
 * GOAL: notice, and fix, the join that silently multiplies your totals.
 * SQL: COUNT(*) vs COUNT(DISTINCT), pre-aggregating in a subquery before joining.
 * NEW EPISODE, 2026-08-24, and the biggest gap the old roadmap had. Fan-out is the most common
 * SILENT bug in workplace SQL: join orders to order_items, SUM the order total, get 3x revenue,
 * and nobody notices until the number is wrong in a meeting. The query SUCCEEDS, which is why it
 * survives review.
 * FRAME IT AS GRAIN — "one row per WHAT?" — never as a join gotcha. Grain is the idea behind
 * correct numbers everywhere, and three other tracks collect on it: Data Modeling Series 1 · 10
 * teaches it from the DESIGN side (what should one row be), Data Warehousing and dbt build on it
 * again. "Watch out for duplicate rows" teaches none of that.
 * Java & Groovy Series 1 · 40 is the SAME bug in memory, where no database warns you.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class JoinFanOutKoans extends KoanBase {

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
