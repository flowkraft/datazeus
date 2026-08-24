package datazeus.javagroovy.series4._20

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 4 · 20
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * GORM — Grails' Data Layer, and the Hibernate Underneath It
 *
 *     zeus.bat koans javagroovy series4 _20     (Windows)
 *     ./zeus.sh koans javagroovy series4 _20    (macOS/Linux)
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile
 * and zeus does not see, so it cannot mislead anyone into thinking the exercise exists.
 * MOVE IT into src/koans/groovy/datazeus/javagroovy/ (same series/episode path) when it is real.
 *
 * Brief below; delete it as you write the koans.
 *
 * ── READ THESE TWO FIRST ────────────────────────────────────────────────
 *   JvmKoanBase                        the rules, and why they are the rules
 *   series2/_35/BatchingAndTransactionsKoans   the worked example
 *
 * ── THE THREE RULES THAT DECIDE EVERY KOAN HERE ─────────────────────────
 *  1. THE BLANK IS A DATA DECISION, NEVER A LANGUAGE FACT. This reader already knows Java.
 *     A koan that teaches map() insults them; one that hands them a wrong number does not.
 *  2. NEVER ASSERT WALL-CLOCK TIME. Every performance lesson here has a countable proxy that
 *     is also the actual lesson. If you think you need a stopwatch, you need a counter.
 *  3. ONE MECHANISM, ONLY THE ASSERTION TARGET CHANGES — a value, a counter, captured output,
 *     or a process result. There is no second framework and there should never be one.
 *
 * THIS LESSON'S RUNGS: koan:diagnose, koan:count:roundtrips
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: work with Grails' data layer, and recognise what it is doing underneath.
 * CONSTRUCTS: domain classes, dynamic finders (findAllByAuthor), where queries, criteria,
 * and the SQL logging that shows you what it actually ran.
 * WHY IT HAS ITS OWN EPISODE: DataPallas custom apps are Grails, so a reader WILL meet it.
 * WHY IT COMES AFTER JPA AND HIBERNATE: GORM is Hibernate underneath, so every word of episodes
 * 00 and 05 applies here too. That through-line is the point of the ordering.
 * KOAN (diagnose + count): a dynamic finder in a loop, and the round trips it produces.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class GormKoans extends JvmKoanBase {

    // TODO: koans, in the same order as the lesson's beats.
    //
    //   value      expect: someTransform(ROWS) == ___
    //   counter    def c = counting(); ...; c.executions == ___
    //              def s = sink();     ...; s.peakHeld  == ___
    //   captured   def log = capturing { ... };  log.contains(...) && !log.contains(SECRET)
    //   process    def r = runTool(Tool, "--in", "x.csv");  r.exit == ___
    //
    // Northwind is already open as `db` (inherited from KoanBase) — use the SAME numbers the
    // SQL koans use wherever the topic allows it. Two tools, one skill, and it costs nothing.
}
