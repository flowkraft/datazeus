package datazeus.learnsql.series2._48

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 48
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * UNION, INTERSECT, EXCEPT — Stacking and Reconciling Result Sets
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series2/_48/ when it is real.
 *
 *     zeus.bat koans learnsql series2 _35     (Windows)
 *     ./zeus.sh koans learnsql series2 _35    (macOS/Linux)
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
 * GOAL: stack two result sets, or subtract one from the other.
 * SQL: UNION, UNION ALL, INTERSECT, EXCEPT, and the column-count/type rules.
 * UNION ALL versus UNION is the practical half: UNION deduplicates and costs a sort, and people
 * reach for it by habit when they wanted UNION ALL.
 *
 * ── KOANS AND THE SERIES CASE (.docs/plan-sql-series2-story.md §0, §3) ─────────────
 * The LESSON reconciles the case's lists (Speedy Express customers EXCEPT customers who received a Speedy
 * delivery: 23; INTERSECT of open and shipped: 24). The KOANS reconcile other lists — countries with
 * customers vs countries with suppliers (UNION / INTERSECT / EXCEPT), products sold in 2023 EXCEPT 2024,
 * UNION vs UNION ALL losing a genuinely repeated row, and one equivalence with NOT EXISTS. Measure first.
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * LEVEL ●● of ●●●●. THE CALLBACK: Series 1 · 40 says FULL OUTER JOIN earns its place in reconciling two
 * lists; EXCEPT and INTERSECT are the set-based way. (The old callback — "S1·40 wrote a FULL OUTER JOIN as
 * a UNION" — was false: S1·40 has no UNION.) A good equivalence koan: an EXCEPT and a NOT EXISTS
 * (Series 2 · 25) return the same rows, on a question different from the lesson's.
 * BUILDS ON: Series 2 · 25, Series 1 · 40. SETS UP: Series 2 · 50, which replaces the UNION ALL report.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class SetOperationsKoans extends KoanBase {

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
