package datazeus._internal

/**
 * Shared plumbing for every JVM koan — Java &amp; Groovy for Data.
 *
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  THE DECISIONS BEHIND THIS FILE — 2026-08-24. Read before extending it.  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * ── 1. THERE IS ONE MECHANISM. ONLY THE ASSERTION TARGET CHANGES. ─────────
 * An earlier design had four named mechanisms for the episodes koans "could not" cover —
 * budget / handover / failure-injection / mutation. That was over-engineering: it made four
 * ordinary tests sound like a framework, and a course that grows clever test infrastructure
 * acquires a maintenance burden nobody asked for.
 *
 * There is one mechanism, the koan. What varies is WHAT you assert on:
 *
 *     a value      the default — Series 1, most of Series 2
 *     a counter    database round trips, or peak rows held      (see counting(), RowSink)
 *     captured     stdout/stderr, or the log after a failure    (see capturing{})
 *     a process    exit code and output of a real jar           (see runJar())
 *
 * That is 29 of this track's 30 episodes. The thirtieth (3/05 project-structure) is marked
 * `eyes` in curriculum.yaml and has no automated check ON PURPOSE — "where the data code
 * goes" is judged by a person, and a test bolted onto it would be theatre.
 *
 * ── 2. NEVER ASSERT WALL-CLOCK TIME. ─────────────────────────────────────
 * This is the rule that makes the whole thing work, and the one most likely to be broken by
 * a well-meaning future addition. Timing assertions are machine-dependent, flaky in CI, and
 * eventually deleted by whoever is on the receiving end of the flake.
 *
 * Every performance lesson in this track has a COUNTABLE proxy that is also the actual
 * lesson. "Slow" is never the assertion; the REASON is:
 *     batching          -> count round trips.      100,000 vs 100. Deterministic.
 *     streaming         -> count peak rows held.   1,000,000 vs 1. Deterministic.
 * A rejected earlier idea was forking a JVM with -Xmx32m so the naive version OOMs. It works,
 * but it costs 30 seconds in a learner's inner loop to prove what a counter proves instantly.
 * If you ever think you need a stopwatch, you need a counter.
 *
 * ── 3. THE BLANK IS A DATA DECISION, NEVER A LANGUAGE FACT. ───────────────
 * Every famous koan set — Ruby, Groovy, Scala — is a LANGUAGE TUTORIAL: `assert 1 + 1 == ___`.
 * The format pulls hard in that direction, and this track must not go there: it is Java &amp;
 * Groovy FOR DATA, for a reader who already knows the language. Koans that teach `map()`
 * insult them; koans that hand them a wrong number do not.
 *
 *     WRONG   assert byCustomer instanceof ___          // what does groupingBy return?
 *     RIGHT   12,000 lines joined to 4,000 orders — what is total revenue?
 *
 * The best example available is 1/25 (joining in memory): it is the SAME join fan-out bug as
 * Learn SQL 2/06, which the reader has already met — except in memory nothing warns you.
 * No database, no constraint, no row count. Same trap, different tool.
 *
 * ── 4. ONE FILE HOLDS BOTH LANGUAGES. VERIFIED, NOT ASSUMED. ─────────────
 * Java runs verbatim inside a Groovy Spock spec. Tested on Groovy 4.0.22: records, `var`,
 * List.of, method references (Order::customer), Collectors.groupingBy/summingDouble, text
 * blocks and java.time all compile and behave identically. So NO src/koans/java, no second
 * source root, no extra compile step.
 *
 * That is not just convenient — it lets one koan show the Java version and the Groovy
 * one-liner side by side, both green, which demonstrates this track's whole thesis ("Java is
 * the default; Groovy where it removes real ceremony") instead of asserting it in a header.
 *
 *     THE ONE TRAP: Groovy's == is equals(), not identity. For data koans that is what you
 *     want. But a koan meant to teach Java reference semantics would silently pass — use
 *     .is() for identity, and prefer not to write that koan at all (see decision 3).
 *
 * ── 5. THE SAME NORTHWIND, ON PURPOSE. ───────────────────────────────────
 * This extends KoanBase, so `db` is the same throwaway Northwind copy the SQL koans use.
 * A learner who found 11 German customers with WHERE should find 11 with filter(). Two tools,
 * one skill, same numbers — and it costs nothing, because the dataset is already open.
 */
abstract class JvmKoanBase extends KoanBase {

    // ══════════════════════════════════════════════════════════════════════
    //  ASSERT ON A COUNTER — "the rows arrive either way; the lesson is how you got them"
    // ══════════════════════════════════════════════════════════════════════

    /**
     * A Connection that counts every trip to the database. Both a naive loop and a batched
     * load produce the right rows, so correctness alone cannot tell them apart — the count
     * can, and the count IS the lesson:
     *
     *     def db = counting(rawConnection())
     *     loadOrders(db, ORDERS)                 // the learner writes this
     *     rowCount("staging") == 100_000         // both versions manage this
     *     db.executions < ___                    // one-at-a-time gives 100,000
     *
     * Implemented with a dynamic proxy so it wraps Statement and PreparedStatement too —
     * otherwise a learner using PreparedStatement.executeBatch() would register as one call
     * and the koan would pass for the wrong reason.
     */
    protected CountingConnection counting(java.sql.Connection real = rawConnection()) {
        return new CountingConnection(real)
    }

    /** The JDBC Connection behind KoanBase's `db`, for koans that need raw JDBC. */
    protected java.sql.Connection rawConnection() { db.connection }

    /**
     * Counts the largest number of rows held in memory AT ONCE.
     *
     * This replaced an earlier design that forked a JVM with -Xmx32m so a naive
     * ResultSet-to-List would OutOfMemory. That works and is dramatic, but it costs ~30s per
     * run and proves exactly what this counter proves instantly. The learner must push rows
     * into the sink one at a time; if they materialise the whole result first, peakHeld is
     * the row count instead of 1.
     */
    protected RowSink sink() { new RowSink() }

    // ══════════════════════════════════════════════════════════════════════
    //  ASSERT ON CAPTURED OUTPUT — for the episodes about what your code SAYS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Run a closure and return everything it printed to stdout and stderr.
     *
     * This is what makes 3/20 ("Logging and Failure — What the 3 a.m. Reader Needs to See")
     * testable, which looked like pure taste until you break the job on purpose and read what
     * it said. The assertions are a PINCER, the same shape as the schema koans — something
     * must appear, and something must not:
     *
     *     def log = capturing { job.process(recordsWithABadOneAt(4712)) }
     *     log.contains("4712")            // WHICH record died
     *     log.contains("transform")       // WHICH stage
     *     log.count("Caused by") == 1     // not swallowed, and not logged twice
     *     !log.contains(SECRET)           // and it did not leak on the way out
     *
     * Captures the streams rather than hooking a logging framework on purpose: the koans must
     * not care whether the learner used SLF4J, System.out or println, and a course that
     * mandates a logging backend to pass an exercise is teaching the wrong thing.
     */
    protected String capturing(Closure work) {
        def buf = new ByteArrayOutputStream()
        def stream = new PrintStream(buf, true, "UTF-8")
        def (oldOut, oldErr) = [System.out, System.err]
        try {
            System.setOut(stream); System.setErr(stream)
            try { work.call() } catch (Exception e) { stream.println("EXCEPTION: ${e}") }
        } finally {
            System.setOut(oldOut); System.setErr(oldErr)
        }
        return buf.toString("UTF-8")
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ASSERT ON A PROCESS — only where assembling the artifact IS the subject
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Call a tool's entry point IN-PROCESS and collect its exit code and output.
     *
     * The contract is `static int run(String[] args)` — deliberately NOT `main`, and this is
     * itself a lesson for 3/10: a main() that calls System.exit is untestable, and extracting
     * an int-returning run() is what makes it testable. (It is also the only option that
     * works: Java 21 removed the SecurityManager that used to let a test intercept exit.)
     *
     * Prefer this over runJar() everywhere except 3/00 and the project. Building a real jar
     * costs ~30 seconds, and 3/10 and 3/15 are about argument handling and configuration —
     * not about assembly.
     */
    protected ToolResult runTool(Class<?> tool, String... args) {
        int code = -1
        String out = capturing {
            code = tool.getMethod("run", String[].class).invoke(null, [args] as Object[]) as int
        }
        return new ToolResult(exit: code, out: out)
    }

    /**
     * Run a real jar in a real subprocess. Slow, so use it ONLY where the artifact assembling
     * correctly is the actual subject: 3/00 (build and dependencies) and 3/45 (the project).
     *
     * It is also what makes 3/15's promise checkable. "Not in the Jar, Not in Git" is not a
     * slogan once you can grep both the output AND the jar bytes for the secret:
     *
     *     def r = runJar(jar, [DB_PASSWORD: SECRET], "--in", "orders.csv")
     *     r.exit == 0
     *     !r.out.contains(SECRET)
     *     !jar.bytes.encodeBase64().toString().contains(...)   // and not baked into the jar
     *
     * The other half of the pincer matters just as much: with the variable ABSENT the tool
     * must fail, non-zero, naming what is missing. A tool that silently uses a default is the
     * bug this episode exists to prevent.
     */
    protected ToolResult runJar(File jar, Map<String, String> env = [:], String... args) {
        def cmd = ["java", "-jar", jar.absolutePath] + (args as List)
        def pb = new ProcessBuilder(cmd).redirectErrorStream(true)
        pb.environment().putAll(env)
        def proc = pb.start()
        def out = proc.inputStream.getText("UTF-8")
        proc.waitFor()
        return new ToolResult(exit: proc.exitValue(), out: out)
    }

    /** What a tool did: the number it returned, and everything it printed. */
    static class ToolResult {
        int exit
        String out
        boolean succeeded() { exit == 0 }
    }
}
