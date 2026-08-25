package datazeus._internal

/**
 * Shared plumbing for every SCHEMA koan — the Data Modeling counterpart to KoanBase.
 *
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  WHY THIS EXISTS, AND WHY IT IS SHAPED LIKE THIS                         ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * A SQL koan blanks a token inside a query and checks the RESULT — the database is the
 * oracle. Modeling looks like it has no oracle, and that missing oracle is exactly the
 * gravity that turns modeling courses into lectures.
 *
 * It has two, and they are what this class exposes:
 *
 *   1. THE INSERTS A SCHEMA REFUSES. "Make bad data impossible" is not a slogan; it is a
 *      testable property. `attempting()` and `shouldReject()` are that test.
 *
 *   2. WHETHER REAL DATA LOADS INTO IT. `loadsRealData()` copies the shipped Northwind rows
 *      into the learner's own tables. Verified against the real 79-row dataset:
 *        PRIMARY KEY("OrderID") on Order Details  -> REJECTED  (grain is OrderID+ProductID)
 *        NOT NULL on "Region"                     -> REJECTED  (genuinely optional)
 *        CHECK("UnitPrice" > 10)                  -> REJECTED  (a 4.50 product exists)
 *        PRIMARY KEY("OrderID","ProductID")       -> 193 rows load
 *      Every rejection names a modeling concept, and no answer key is involved.
 *
 * ── THE LADDER — and why almost nothing here asks for DDL ─────────────────
 * If a koan says "write the schema", the learner's effort goes into CREATE TABLE syntax,
 * which is not the subject. So the koans climb, and only the top rung authors a model:
 *
 *   rung 1  PREDICT    attempting(sql) == ___            schema given; types nothing
 *   rung 2  DIAGNOSE   grainOf(t) / canAnswer(sql) == ___          types nothing
 *   rung 3  CHOOSE     modelThatSurvives(...) == ___               types nothing
 *   rung 4  COMPLETE   ddlMakesImpossible(ddlWith___, badInsert)   ONE blanked line
 *   rung 5  AUTHOR     loadsRealData(table)                        projects only
 *
 * ── THE RULE THAT KEEPS A SUITE HONEST ────────────────────────────────────
 * PAIR EVERY shouldReject WITH A shouldAccept. A learner who only ever sees rejections
 * learns to bolt constraints onto everything and call it rigour. The pincer — this must
 * bounce, AND this must still get through — is the judgement actually being taught.
 *
 * ── WHY REBUILD AND NEVER ALTER ───────────────────────────────────────────
 * Tested on DuckDB v1.5.4: ALTER TABLE ADD PRIMARY KEY and ALTER COLUMN SET NOT NULL work,
 * but ADD FOREIGN KEY, ADD UNIQUE and ADD CHECK are all "not implemented". So a learner
 * cannot bolt integrity onto Northwind in place — schema.sql is CREATE TABLE with the
 * constraints inline, then INSERT ... SELECT from main. That is why applySchema() drops and
 * recreates rather than mutating, and why the whole thing is safely re-runnable.
 *
 * ── SAFETY ────────────────────────────────────────────────────────────────
 * KoanBase already opens a THROWAWAY COPY of northwind.duckdb, so nothing here can touch the
 * shipped dataset, the learner's CloudBeaver session, or the e2e tests that run against it.
 * The practice schema lives inside that copy and dies with it.
 *
 * DO NOT "FIX" THE SHIPPED DATASET so these koans have constraints to inspect. Its lack of
 * declared PKs and FKs is the entire premise of Series 1 — see the "TWO DATASETS, TWO JOBS"
 * block in the header of courses/datamodeling/curriculum.yaml.
 */
abstract class SchemaKoanBase extends KoanBase {

    /** The schema the learner builds in. Everything they create is namespaced here. */
    protected static final String PRACTICE = "practice"

    /** Outcomes for the predict rung. Strings so a wrong guess reads well in the hint. */
    protected static final String REJECTED = "REJECTED"
    protected static final String ACCEPTED = "ACCEPTED"

    def setupSpec() {
        // Its own statement, never batched with anything that references main.* — DuckDB
        // binds the whole batch before the schema exists and the qualified reference fails.
        db.execute("CREATE SCHEMA IF NOT EXISTS ${PRACTICE}".toString())
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RUNG 1 — PREDICT.  The schema is given; the learner decides what the DB will do.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Run a statement and report whether the model let it through. Returns REJECTED or
     * ACCEPTED so the koan reads `attempting("INSERT ...") == ___`.
     *
     * Deliberately does NOT distinguish which constraint fired: naming the constraint is the
     * learner's job, and a hint that says "unique violation on line 3" answers the koan.
     */
    protected String attempting(String sql) {
        try {
            db.execute(sql)
            return ACCEPTED
        } catch (Exception ignored) {
            return REJECTED
        }
    }

    /** Assertion form: this statement MUST bounce. `because` becomes the hint when it doesn't. */
    protected boolean shouldReject(String sql, String because) {
        if (attempting(sql) == ACCEPTED) {
            throw new KoanHint("that INSERT went through, but the model should have stopped it:\n" +
                    "  ${because}\n" +
                    "the constraint that would have caught it is missing.")
        }
        return true
    }

    /**
     * Assertion form: this statement MUST get through. The other jaw of the pincer — without
     * it a learner "passes" by making everything impossible.
     */
    protected boolean shouldAccept(String sql) {
        try {
            db.execute(sql)
            return true
        } catch (Exception e) {
            throw new KoanHint("the model rejected something it should allow:\n" +
                    "  ${firstLineOf(e)}\n" +
                    "a constraint is stricter than the business actually is.")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RUNG 2 — DIAGNOSE.  Name the property; don't build anything.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * The real grain of a table: the SMALLEST set of columns that is unique across every row.
     * Searches combinations of 1, then 2, then 3 columns and returns the first that holds, so
     * a koan reads `grainOf('"Order Details"') == ___`.
     *
     * Answering "OrderID" for Order Details is the mistake nearly everyone makes, and the
     * hint shows them the arithmetic that disproves it rather than the answer.
     */
    protected List<String> grainOf(String table) {
        List<String> cols = columnsOf(table)
        long total = firstCell("SELECT count(*) FROM ${table}".toString()) as long
        for (int size = 1; size <= 3; size++) {
            for (List<String> combo : combinations(cols, size)) {
                String list = combo.collect { "\"${it}\"" }.join(", ")
                long distinct = firstCell("SELECT count(*) FROM (SELECT DISTINCT ${list} FROM ${table}) x".toString()) as long
                if (distinct == total) return combo
            }
        }
        throw new KoanHint("no combination of up to three columns is unique in ${table} — " +
                "either it has no natural grain, or the grain is wider than this koan can find.")
    }

    /** Assertion form, for when discovery would give the answer away. */
    protected boolean grainIs(String table, List<String> columns) {
        long total = firstCell("SELECT count(*) FROM ${table}".toString()) as long
        String list = columns.collect { "\"${it}\"" }.join(", ")
        long distinct = firstCell("SELECT count(*) FROM (SELECT DISTINCT ${list} FROM ${table}) x".toString()) as long
        if (distinct != total) {
            throw new KoanHint("${columns} is not the grain of ${table}:\n" +
                    "  ${total} rows, but only ${distinct} distinct ${columns}.\n" +
                    "  more than one row shares the same value — so it cannot identify a row.")
        }
        return true
    }

    /**
     * Whether the model RETAINED enough to answer a question at all. A model can satisfy every
     * constraint and still be unable to say how many copies of a title exist, because the
     * information was never stored. That is the failure this catches, and it is the one that
     * costs the most in real life.
     */
    protected boolean canAnswer(String sql) {
        try {
            db.rows(sql)
            return true
        } catch (Exception ignored) {
            return false
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RUNG 3 — CHOOSE.  Finished models, given; which one survives?
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Candidate models for a choose-koan, as label -> DDL. Build them in the lesson's koan
     * file, then ask which one answers the probe query. Each is created in its own schema
     * (practice_a, practice_b, …) so all three coexist and can be compared side by side.
     *
     * TODO(harness): implement when the first choose-koan is written — the contract is
     *   givenModels(A: ddl, B: ddl, C: ddl)  then
     *   modelThatSurvives(probeSql) returns the single label whose probe returns the right
     *   answer, and the hint SHOWS the wrong answers the others gave. Showing model A
     *   cheerfully returning today's price for a March order is the entire lesson; a bare
     *   "wrong, it's B" throws that away.
     */
    protected void givenModels(Map<String, String> ddlByLabel) {
        throw new UnsupportedOperationException("givenModels: not implemented yet — see the TODO above")
    }

    /** @see #givenModels */
    protected String modelThatSurvives(String scenario) {
        throw new UnsupportedOperationException("modelThatSurvives: not implemented yet — see givenModels")
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RUNG 4 — COMPLETE.  Boilerplate given, one blanked line: the decision itself.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * The learner fills the single blanked line in `ddl`, and the koan passes only if the
     * finished model REFUSES `badInsert`. All the surrounding CREATE TABLE noise is given, so
     * their attention is on the one line that is the lesson.
     */
    protected boolean ddlMakesImpossible(String ddl, String badInsert) {
        if (ddl == null || ddl.contains("___")) {
            throw new KoanHint("you haven't made the decision yet — replace the ___ with the one\n" +
                    "line that makes this impossible:\n  ${badInsert}")
        }
        applySchema(ddl)
        return shouldReject(badInsert, "this is the thing the blanked line had to prevent")
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RUNG 5 — AUTHOR.  The whole model. Projects only.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * THE ORACLE THAT MAKES THIS TRACK WORK. Copy the shipped Northwind rows into the table
     * the learner built. It succeeds only if their model actually fits reality — not ours.
     *
     * Nothing is compared against an answer key. 79 rows of real business data are the grader,
     * and every way they can fail is a modeling concept with a name.
     */
    protected boolean loadsRealData(String table) {
        String target = "${PRACTICE}.${table}"
        try {
            db.execute("INSERT INTO ${target} SELECT * FROM main.${table}".toString())
        } catch (Exception e) {
            throw new KoanHint("the real Northwind rows do not fit your model:\n" +
                    "  ${firstLineOf(e)}\n" +
                    "the data is not wrong — read what it is telling you about your schema.")
        }
        long loaded = firstCell("SELECT count(*) FROM ${target}".toString()) as long
        long expected = firstCell("SELECT count(*) FROM main.${table}".toString()) as long
        if (loaded != expected) {
            throw new KoanHint("only ${loaded} of ${expected} rows landed in ${table}.")
        }
        return true
    }

    /**
     * Drop and rebuild the practice schema from the learner's DDL. A REBUILD, never an ALTER —
     * DuckDB cannot ADD FOREIGN KEY / UNIQUE / CHECK to an existing table (see the class doc),
     * and rebuilding is what makes schema.sql idempotent and safe to re-run all series long.
     *
     * Statements are executed ONE AT A TIME on purpose: DuckDB binds a whole batch up front,
     * so a batch that creates the schema and then references main.* in the same breath fails.
     */
    protected void applySchema(String ddl) {
        db.execute("DROP SCHEMA IF EXISTS ${PRACTICE} CASCADE".toString())
        db.execute("CREATE SCHEMA ${PRACTICE}".toString())
        ddl.split(";").findAll { it.trim() }.each { stmt ->
            try {
                db.execute(stmt.trim())
            } catch (Exception e) {
                throw new KoanHint("your DDL didn't run: ${firstLineOf(e)}\n  ${stmt.trim().readLines().first()}")
            }
        }
    }

    // ── small helpers ─────────────────────────────────────────────────────

    private List<String> columnsOf(String table) {
        String bare = table.replaceAll('"', '')
        return db.rows("SELECT column_name FROM information_schema.columns WHERE table_name = '${bare}' ORDER BY ordinal_position".toString())
                .collect { it.values().first() as String }
    }

    private static List<List<String>> combinations(List<String> items, int size) {
        if (size == 0) return [[]]
        if (items.size() < size) return []
        List<List<String>> out = []
        items.eachWithIndex { item, i ->
            combinations(items[(i + 1)..<items.size()] as List<String>, size - 1).each { out << ([item] + it) }
        }
        return out
    }

    private static String firstLineOf(Exception e) {
        String msg = e.message ?: "no detail"
        String first = msg.readLines().find { it?.trim() } ?: msg
        return first.length() > 110 ? first.substring(0, 110) + " ..." : first
    }
}
