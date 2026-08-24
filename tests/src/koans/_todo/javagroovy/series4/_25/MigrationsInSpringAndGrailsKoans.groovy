package datazeus.javagroovy.series4._25

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 4 · 25
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Liquibase in Spring Boot and Grails — Migrations That Run at Startup
 *
 *     zeus.bat koans javagroovy series4 _25     (Windows)
 *     ./zeus.sh koans javagroovy series4 _25    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:process:jar, koan:complete
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: run the same changelog automatically when either app starts.
 * CONSTRUCTS: spring.liquibase.change-log and spring.liquibase.enabled for Boot; the
 * grails-data-hibernate5-dbmigration plugin for Grails; DDL_AUTO=none when Liquibase owns the schema.
 * THE OTHER HALF OF SERIES 3 · 20, which ran the same tool from the build with no framework.
 * BOTH PLAYGROUNDS ARE ALREADY SCAFFOLDED AND BOTH CHANGELOGS ARE EMPTY — bkend-boot-groovy-playground
 * and grails-playground, both on liquibase-groovy-dsl 4.0.1. The exercise is a real file in a real
 * product, which is the most concrete thing this track can offer short of the project.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class MigrationsInSpringAndGrailsKoans extends JvmKoanBase {

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
