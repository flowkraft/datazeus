package datazeus.dataops.series1._32

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  KOANS — Data Ops · Series 1 · 32
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Connection Failures — Host, Port, Firewall, Credentials and TLS
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/dataops/series1/_32/ when it is real.
 *
 *     zeus.bat koans dataops series1 _32     (Windows)
 *     ./zeus.sh koans dataops series1 _32    (macOS/Linux)
 *
 * ── READ THESE FIRST ────────────────────────────────────────────────────
 *   _internal/KoanBase.groovy       the ___ blank and the assertion helpers
 *   _internal/JvmKoanBase.groovy    process and counter helpers
 *   courses/dataops/curriculum.yaml   the track's decisions
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
 *   DIAGNOSE — hand them something already broken and ask WHY. No fix required.
 *     The habit this builds is noticing, which is the part nobody teaches.
 *   CAPTURE — assert on what was PRINTED. stdout/stderr after the thing failed.
 *   SURFACE `terminal` — a shell — no database, no Python
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: TODO — one sentence.
 * THE BLANK TURNS ON: TODO — the specific number/value that is either right or quietly,
 * plausibly wrong. This is the only part of this file a machine could not write for you.
 */
@Stepwise
class WhenYouCannotConnectKoans extends KoanBase {

    // TODO: koans, one per idea in the lesson, in the lesson's order.
}
