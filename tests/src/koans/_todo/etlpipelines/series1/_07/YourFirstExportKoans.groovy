package datazeus.etlpipelines.series1._07

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  KOANS — ETL & Data Pipelines · Series 1 · 07
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Your First Export — Write a Table Out as a File Someone Else Can Load
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/etlpipelines/series1/_07/ when it is real.
 *
 *     zeus.bat koans etlpipelines series1 _07     (Windows)
 *     ./zeus.sh koans etlpipelines series1 _07    (macOS/Linux)
 *
 * ── READ THESE FIRST ────────────────────────────────────────────────────
 *   _internal/KoanBase.groovy       the ___ blank and the assertion helpers
 *   _internal/JvmKoanBase.groovy    process and counter helpers
 *   courses/etlpipelines/curriculum.yaml   the track's decisions
 *
 * ── THE RULES ───────────────────────────────────────────────────────────
 *  1. THE BLANK IS A DATA DECISION, NEVER A SYNTAX FACT. If a blank can be answered by
 *     reading the docs instead of the data, it is the wrong blank.
 *  2. NEVER ASSERT WALL-CLOCK TIME. Every performance lesson has a countable proxy that is
 *     also the lesson. If you think you need a stopwatch, you need a counter.
 *  3. ONE MECHANISM, only the assertion target changes.
 *  4. Use the SAME Northwind numbers every other track uses wherever the topic allows it.
 *
 * ── WHAT THIS EPISODE'S RUNGS OBLIGE YOU TO WRITE ───────────────────────
 *   AUTHOR — no scaffolding. They write the whole thing, graded on the result.
 *   RECONCILE — the answer is proved against the source it came from: rows and a total.
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: write a day of Orders out as a CSV, read it back, and prove it matches the table.
 * THE BLANK TURNS ON: the day's freight read back from the learner's own file (591.26 for
 * 2024-12-23, 12 orders, 2 with an empty ShippedDate). A file that split order 9922's comma
 * note, or skipped the row, still loads — and comes back short by 73.13.
 * DATASET: extends NorthwindCoKoanBase when real (Northwind Company S, schema northwind_co_s).
 */
@Stepwise
class YourFirstExportKoans extends KoanBase {

    // TODO: koans, one per idea in the lesson, in the lesson's order.
}
