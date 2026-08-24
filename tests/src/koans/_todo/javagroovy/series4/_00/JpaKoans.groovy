package datazeus.javagroovy.series4._00

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 4 · 00
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * JPA — Java's Standard Way to Turn Rows Into Objects
 *
 *     zeus.bat koans javagroovy series4 _00     (Windows)
 *     ./zeus.sh koans javagroovy series4 _00    (macOS/Linux)
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
 * GOAL: understand the standard Java uses to map objects to tables, and what it does at runtime.
 * CONSTRUCTS: @Entity, EntityManager, persist/merge/find, the persistence context, JPQL,
 * lazy vs eager, and the N+1 that hides inside lazy loading.
 * JPA IS A SPEC (jakarta.persistence) and runs in plain Java SE with no framework — a reader who
 * only meets it through Spring Data never learns what belongs to which. That is why it has its
 * own episode and why it comes first in this series.
 * KOAN (diagnose) with the round-trip counter: a loop that looks like field access is one query
 * per iteration. The code looks innocent; the counter says 1,001.
 * NOT core: this track's spine is SQL-first, and marking the ORM path core would undercut the
 * argument the first three series spend thirty episodes making.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class JpaKoans extends JvmKoanBase {

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
