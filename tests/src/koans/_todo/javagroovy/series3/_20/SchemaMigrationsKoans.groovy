package datazeus.javagroovy.series3._20

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 3 · 20
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Liquibase — Database Changes Kept in Version Control, Written in Groovy
 *
 *     zeus.bat koans javagroovy series3 _20     (Windows)
 *     ./zeus.sh koans javagroovy series3 _20    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:complete, koan:process:tool
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: change a database schema the same way you change code — versioned, reviewed, repeatable.
 * CONSTRUCTS: Liquibase changelog in the GROOVY DSL, changesets, preconditions, the Maven/Gradle
 * plugin, and the tracking table.
 * LIQUIBASE, DECIDED ON EVIDENCE — see this file's episode comment for the full reasoning.
 * Short version: DataPallas already ships it in both playgrounds with liquibase-groovy-dsl, both
 * changelog.groovy files are EMPTY, Grails' official plugin IS Liquibase, and Flyway Community's
 * tiered database matrix is exactly what bites a five-engine house.
 * FRAMEWORK-FREE HERE: the build plugin, on plain JDBC. Series 4 · 25 wires the same changelog
 * into Spring Boot and Grails. Tool first, framework integration after.
 * THE EXERCISE IS A REAL FILE: "here is the changelog scaffolded in your own product, and nothing
 * is in it."
 */
@Stepwise // walk them in order — once one fails, the rest wait
class SchemaMigrationsKoans extends JvmKoanBase {

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
