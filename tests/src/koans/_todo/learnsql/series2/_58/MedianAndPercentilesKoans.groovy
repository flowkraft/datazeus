package datazeus.learnsql.series2._58

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 58
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Median and Percentiles — WITHIN GROUP, and Why AVG Is Not the Answer
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/learnsql/series2/_58/ when it is real.
 *
 *     zeus.bat koans learnsql series2 _58     (Windows)
 *     ./zeus.sh koans learnsql series2 _58    (macOS/Linux)
 *
 * WHY THIS FILE EXISTS: written 2026-09-15 — the episode had a lesson brief and no koan brief.
 *
 * ── READ THESE FIRST ────────────────────────────────────────────────────
 *   KoanBase                                  shouldReturn, the ___ blank, the dataset
 *   learnsql/series1/_10/WhereFilteringKoans   the worked example: twelve koans, one per idea
 *   courses/learnsql/series2-intermediate/58-median-and-percentiles/_todo-58-median-and-percentiles.mdx   the lesson brief
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
 * GOAL: say what is typical — the median, percentiles for service levels, a median per group, NTILE
 * quartiles, and the ROW_NUMBER workaround where the engine has no percentile function.
 * HANDS-ON (curriculum.yaml): koan:predict, koan:complete — `predict` on PERCENTILE_CONT against
 * PERCENTILE_DISC over an EVEN-sized group (nobody gets both right first time); `complete` on a
 * median per group.
 *
 * ── KOANS AND THE SERIES CASE (.docs/plan-academy-course-stories-artefacts.md §1, §3.2) ─────────────
 * The LESSON's main example is average vs median order value (736.12 vs 506.17); its case file defines
 * "late" from delivery times (median 6, slowest 9) and confirms the reorder gaps (190 vs 190). The KOANS
 * use other questions: the median unit price per category (pick a category with an even count for CONT vs
 * DISC); p90 freight per shipper; NTILE(4) of products by price; the ROW_NUMBER median workaround on a
 * small group. MODE / PERCENT_RANK / CUME_DIST at most one koan. Measure every figure first, on both engines.
 *
 * ── PLACE IN THE SERIES ─────────────────────────────────────────────────
 * LEVEL ●●●● of ●●●●. BUILDS ON: Series 2 · 35 (ROW_NUMBER), Series 1 · 25 (AVG).
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class MedianAndPercentilesKoans extends KoanBase {

    // TODO: koans, one per idea in the lesson, in the same order. See _42's brief for the
    // fragment-koan and whole-query-koan shapes.
}
