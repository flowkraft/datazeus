package datazeus.learnsql.series2._60

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 60
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Project — Answering a Real Business Question End to End
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
 *   learnsql/series2/_06/JoinFanOutKoans       the fan-out this project WILL walk into
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
 * SQL: nothing new. Multi-table joins (00), a subquery or EXISTS (05), a CTE to name the steps
 * (08), CASE for the buckets (15), a date range with the right boundaries (20), and a window
 * function for the rank or the running total (50/55). That is the point — a project introduces
 * no syntax.
 *
 * THE RUNGS ARE `koan:author` + `cloudbeaver`, and the author rung is the whole exercise:
 * NO SCAFFOLDING. One `___` for the entire query, graded on the row set. Anything less makes
 * it another drill, and this series already has twelve of those.
 *
 * ── THE QUESTION TO PICK, AND WHY IT MATTERS WHICH ──────────────────────
 * It has to be a question with a WRONG answer that looks right. Otherwise "end to end" just
 * means "longer". The best candidates all share that shape:
 *   - revenue per customer per quarter, ranked, for a date range — walks straight into the
 *     join fan-out from 06 (freight or an order-level column summed once per LINE), and into
 *     the date boundary from 20 (a range that quietly drops the last day).
 *   - customers who bought in Q1 and NOT since — the classic where a LEFT JOIN and a NOT
 *     EXISTS give different answers, and only one is right.
 * Whichever you choose, the article must state the number a careful person would get WRONG,
 * and the koan must be able to tell the two apart. If the wrong answer and the right answer
 * produce the same row set, it is the wrong question for this episode.
 *
 * ── AND ONE KOAN THAT IS NOT ABOUT SQL ──────────────────────────────────
 * End the file with the habit, not the query: after the join, did the number of distinct
 * customers change? That check is the transferable skill of this whole series, it is three
 * lines, and it is the thing that would have caught the wrong answer above. Java & Groovy
 * 1 · 40 and Python 1 · 35 close on the same check on purpose.
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
