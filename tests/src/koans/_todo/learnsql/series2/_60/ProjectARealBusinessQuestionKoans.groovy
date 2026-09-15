package datazeus.learnsql.series2._60

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 60
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Subqueries + CTEs + Window Functions — Solving a Real Business Case, End to End
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series2/_60/ when it is real.
 *
 *     zeus.bat koans learnsql series2 _60     (Windows)
 *     ./zeus.sh koans learnsql series2 _60    (macOS/Linux)
 *
 * WHY THIS FILE EXISTS AT ALL: it was the ONE episode in Learn SQL with no koan brief — 34 of
 * 35 had one, and the gap was invisible until the track declared `hands_on` on 2026-08-24 and
 * something finally compared the two lists. The missing one being the SERIES PROJECT is the
 * worst possible place for it: the episode where everything gets applied is the episode a
 * reader most needs graded.
 *
 * ── READ THESE FIRST ────────────────────────────────────────────────────
 *   KoanBase                                   shouldReturn, the ___ blank, the dataset
 *   learnsql/series1/_10/WhereFilteringKoans   the worked example: twelve koans, one per idea
 *   learnsql/series2/_15/MultiTableJoinsDuplicateRowsKoans   the fan-out this project WILL walk into
 *
 * ── THE RULES ───────────────────────────────────────────────────────────
 *  1. THE BLANK GOES INSIDE THE SQL. The learner WRITES THE QUERY; the koan checks the
 *     RESULT. You learn SQL by writing queries, not by typing in a number.
 *  2. ONE KOAN PER IDEA IN THE LESSON, IN THE LESSON'S ORDER.
 *  3. PREDICT FIRST — word each koan so the answer can be said out loud before running it.
 *  4. END WITH A WHOLE-QUERY KOAN. A row-set expectation is fake-resistant in a way a single
 *     count is not.
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: take one question a person would actually be asked at work, and answer it correctly
 * end to end — not a drill, and not a query whose right answer was given in the question.
 * SQL: nothing new. Subqueries (00) and EXISTS (25), multi-table joins and their grain (15), a CTE to name the steps
 * (20), CASE for the buckets (05), a date range with the right boundaries (10), and a window
 * function for the rank or the running total (35/45). That is the point — a project introduces
 * no syntax.
 *
 * THE RUNGS ARE `koan:author` + `cloudbeaver`, and the author rung is the whole exercise:
 * NO SCAFFOLDING. One `___` for the entire query, graded on the row set. Anything less makes
 * it another drill, and this series already has twelve of those.
 *
 * ── THE QUESTION — CHOSEN 2026-09-15 ────────────────────────────────────
 * The lesson answers the series case: a third of what we sold has no shipping record — did
 * those orders really not ship, or did nobody record it? (customers, reps, couriers). The
 * KOANS must not copy it: build THE SAME CASE FROM THE SUPPLIER SIDE, on "Suppliers",
 * "Products" and "Order Details" — whose goods sit in the orders with no ShippedDate, as a share
 * of each supplier's sales — the way Series 1 · 50's koans built the supplier side of that
 * lesson's report. Keep the property that matters: each step has a wrong answer that looks
 * right (a fan-out, a 0% share from integer division, a count that moved after a join), and
 * the koan can tell it apart. MEASURE every figure first.
 *
 * ── AND ONE KOAN THAT IS NOT ABOUT SQL ──────────────────────────────────
 * End the file with the habit, not the query: after the join, did the number of distinct
 * customers change? That check is the transferable skill of this whole series, it is three
 * lines, and it is the thing that would have caught the wrong answer above. Java & Groovy
 * 1 · 40 and Python 1 · 35 close on the same check on purpose.
 *
 * ── KOANS AND THE SERIES CASE (.docs/plan-sql-series2-story.md §0, §3) ─────────────
 * See THE QUESTION above: the koans build the same case from the supplier side, on different tables, and end
 * on the habit check (the key count survives every join).
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * The last episode of the series; it may use anything above it.
 * DATA: the lesson's figures are in .docs/plan-sql-series2-story.md §2 (DuckDB 2026-09-15); the
 * supplier-side figures are not measured yet.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class ProjectARealBusinessQuestionKoans extends KoanBase {

    // TODO: build up to the whole query, then the habit check.
    //
    //   1..n  the steps, each a whole-query koan in its own right — the join, then the
    //         grouping, then the window — so a reader who stalls knows WHICH step broke.
    //
    //     def "revenue per customer, for 1997 only"() {
    //         expect:
    //         shouldReturn([["QUICK", 61109.90], /* … */], '''
    //             ___
    //         ''')
    //     }
    //
    //   last  the habit: the key count must survive the join.
    //
    //     def "the join did not invent or lose a customer"() {
    //         expect:
    //         shouldReturn 89, '''
    //             ___
    //         '''
    //     }
}
