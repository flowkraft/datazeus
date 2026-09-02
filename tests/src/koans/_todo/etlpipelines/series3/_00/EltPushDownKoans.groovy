package datazeus.etlpipelines.series3._00

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  KOANS — ETL & Data Pipelines · Series 3 · 00
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ETL vs ELT — Where the Transform Should Run, Now That You Have Done Both
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/etlpipelines/series3/_00/ when it is real.
 *
 *     zeus.bat koans etlpipelines series3 _00     (Windows)
 *     ./zeus.sh koans etlpipelines series3 _00    (macOS/Linux)
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
 *   EQUIVALENT — two queries/models must return the SAME result set. Assert the
 *     ROWS, never the text of the SQL — that is what makes it gradeable at all.
 *   COUNT:SCANNED — assert rows or bytes the engine actually READ. This is the
 *     legal replacement for a stopwatch. NEVER assert elapsed time.
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: TODO — one sentence.
 * THE BLANK TURNS ON: TODO — the specific number/value that is either right or quietly,
 * plausibly wrong. This is the only part of this file a machine could not write for you.
 */
@Stepwise
class EltPushDownKoans extends KoanBase {

    // TODO: koans, one per idea in the lesson, in the lesson's order.
}
