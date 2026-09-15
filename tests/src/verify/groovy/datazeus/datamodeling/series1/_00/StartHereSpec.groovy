package datazeus.datamodeling.series1._00

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

import java.sql.SQLException

/**
 * VERIFIED spec = the PUBLISH GATE for Data Modeling · Series 1 · lesson 00
 * "Start Here: The Modeling Loop — From What a Business Does to Tables That Prove It".
 *
 * Every figure, statement and refusal the video, the article and the checks put in front of a
 * learner is asserted here, on BOTH engines. The lesson's eight scripts, run in the order the
 * video runs them:
 *
 *    1. count-categories              — the rows you are about to model: 8                    §1
 *    2. create-categories             — CREATE TABLE practice."Categories", key + NOT NULL     §2
 *    3. load-categories               — the real rows fit: 8 loaded                            §2
 *    4. count-your-categories         — and your table says 8                                  §2
 *    5. insert-a-second-category-1    — REFUSED: the key already exists (23505)               §3
 *    6. insert-a-new-category         — ACCEPTED: 9, Frozen Foods — the other jaw              §3
 *    7. insert-a-nameless-category    — REFUSED: a category with no name (23502)              §3
 *    8. list-your-categories          — what is left: 9 rows, 1 to 9                           §3
 *
 * §4 holds the scripts to the course's conventions, §5 runs the CHECKS FILE itself against the
 * reference solution, and §6 proves each check can FAIL — a check no model can fail checks nothing.
 *
 * ── WHY A SCHEMA OF ITS OWN, NOT `practice` ──────────────────────────────────────────────
 * NorthwindEngines hands every spec in the JVM the SAME DuckDB copy and the SAME PostgreSQL, built
 * for read-only lessons. This lesson WRITES. So everything it builds lives in `dm_s1_00` — every
 * `practice` in the scripts, the checks and the reference is rewritten to it — and is dropped in
 * cleanupSpec. Nothing another spec reads is touched, and Northwind's own tables never are.
 *
 * ── THE ERROR TEXT IS ASSERTED VERBATIM ON POSTGRESQL ────────────────────────────────────
 * The video's ErrorPanel shows PostgreSQL's SQLSTATE, message and DETAIL line, because that is
 * what CloudBeaver prints and what a learner running the INSERT will compare against. DuckDB
 * words the same refusals differently; its wording is asserted too, so the article can quote it.
 */
class StartHereSpec extends NorthwindGateSpec {

    static final String SCHEMA = "dm_s1_00"

    def cleanupSpec() {
        ENGINES.each { e ->
            sqlFor(e).execute("DROP TABLE IF EXISTS probe_shipper".toString())
            sqlFor(e).execute("DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE".toString())
        }
    }

    // --- 1. The rows the lesson models ----------------------------------------------------

    @Unroll
    def "[#engine] Northwind has 8 categories and 3 shippers — the lesson's table and the learner's"() {
        expect:
        sqlFor(engine).firstRow(script("count-categories")).values().first() == 8
        sqlFor(engine).firstRow('SELECT count(*) FROM "Shippers"').values().first() == 3

        where:
        engine << ENGINES
    }

    // --- 2. Build it, load it: the real rows fit ------------------------------------------

    @Unroll
    def "[#engine] the lesson's CREATE TABLE builds, the 8 real rows load, and the table says 8"() {
        given:
        fresh(engine)

        when:
        sqlFor(engine).execute(script("create-categories"))
        int loaded = sqlFor(engine).executeUpdate(script("load-categories"))

        then:
        loaded == 8
        sqlFor(engine).firstRow(script("count-your-categories")).values().first() == 8

        where:
        engine << ENGINES
    }

    // --- 3. Try to break it: two refusals, and the accept between them --------------------

    @Unroll
    def "[#engine] a second category 1 is refused, category 9 gets in, a nameless category is refused — 9 rows"() {
        given:
        fresh(engine)
        sqlFor(engine).execute(script("create-categories"))
        sqlFor(engine).executeUpdate(script("load-categories"))

        when:
        def duplicate = refusal(engine, script("insert-a-second-category-1"))
        def accepted = refusal(engine, script("insert-a-new-category"))
        def nameless = refusal(engine, script("insert-a-nameless-category"))
        def rows = sqlFor(engine).rows(script("list-your-categories"))

        then: "the duplicate key bounces"
        duplicate != null
        if (engine == "postgres") {
            assert duplicate.state == "23505"
            assert duplicate.message == 'duplicate key value violates unique constraint "Categories_pkey"'
            assert duplicate.detail == 'Key ("CategoryID")=(1) already exists.'
        } else {
            assert duplicate.message.contains('Duplicate key "CategoryID: 1" violates primary key constraint')
        }

        and: "the good row still loads — the other jaw of the pincer"
        accepted == null

        and: "the nameless row bounces"
        nameless != null
        if (engine == "postgres") {
            assert nameless.state == "23502"
            assert nameless.message == 'null value in column "CategoryName" of relation "Categories" violates not-null constraint'
            assert nameless.detail == 'Failing row contains (10, null, A category with no name).'
        } else {
            assert nameless.message.contains('NOT NULL constraint failed: Categories.CategoryName')
        }

        and: "what is left is the 8 real rows and Frozen Foods"
        rows.collect { it.CategoryID as int } == (1..9).toList()
        rows[0].CategoryName == "Beverages"
        rows[8].CategoryName == "Frozen Foods"

        where:
        engine << ENGINES
    }

    // --- 4. The scripts keep the course's conventions -------------------------------------

    def "every script reads Northwind UNQUALIFIED, writes to practice, and loads in order"() {
        given:
        def all = new File(SCRIPTS).listFiles().findAll { it.name.endsWith(".sql") }

        expect:
        all.size() == 8
        all.every { !(it.text =~ /(?i)\b(main|public)\./) }
        all.findAll { it.text =~ /(?i)\bINSERT\b/ }.every { it.text.contains('practice."') }
        all.findAll { it.text =~ /(?i)INSERT[\s\S]*SELECT/ }.every { it.text =~ /(?i)ORDER BY/ }
        all.every { f -> f.text.readLines().every { it.length() <= 45 } }
        all.every { !it.text.contains("--") }
    }

    // --- 5. The checks file, run against the reference solution ---------------------------

    /** Episode 00's checks, in the order the file runs them. The names are on screen, verbatim. */
    static final List<String> CHECKS_00 = [
            "Northwind's 3 shippers fit your table",
            "a second shipper number 1 is refused",
            "a shipper with no name is refused",
            "a new shipper, number 4, still gets in",
    ]

    def "the checks file carries exactly episode 00's four checks, in order"() {
        expect:
        (section("00") =~ /def "(.+?)"\(\)/).collect { it[1] } == CHECKS_00
    }

    @Unroll
    def "[#engine] every episode 00 check PASSES on the reference solution"() {
        given:
        applyReference(engine, reference())

        expect:
        CHECKS_00.every { verdict(engine, it) }

        where:
        engine << ENGINES
    }

    // --- 6. Every check can fail ----------------------------------------------------------

    @Unroll
    def "[#engine] '#check' FAILS on a model that #flaw"() {
        given:
        def model = reference()
        assert model.count(from) == 1: "the reference no longer contains: ${from}"
        applyReference(engine, model.replace(from, to))

        expect:
        CHECKS_00.takeWhile { it != check }.every { verdict(engine, it) }   // the checks before it still pass
        !verdict(engine, check)

        where:
        [engine, check, flaw, from, to] << ENGINES.collectMany { e ->
            [
                    [e, "a second shipper number 1 is refused", "has no key",
                     '"ShipperID"   INTEGER PRIMARY KEY,', '"ShipperID"   INTEGER,'],
                    [e, "a shipper with no name is refused", "lets the name be empty",
                     '"CompanyName" VARCHAR NOT NULL,', '"CompanyName" VARCHAR,'],
                    [e, "a new shipper, number 4, still gets in", "is stricter than the business",
                     '"ShipperID"   INTEGER PRIMARY KEY,', '"ShipperID"   INTEGER PRIMARY KEY CHECK ("ShipperID" <= 3),'],
                    [e, "Northwind's 3 shippers fit your table", "never loads the rows",
                     'FROM "Shippers"\nORDER BY "ShipperID"', 'FROM "Shippers"\nWHERE 1 = 0\nORDER BY "ShipperID"'],
            ]
        }
    }

    // --- helpers --------------------------------------------------------------------------

    static final String SCRIPTS = "../courses/datamodeling/series1-modeling-fundamentals/00-start-here/scripts"
    static final String CHECKS = "src/koans/groovy/datazeus/datamodeling/series1/NorthwindModelChecks.groovy"
    static final String REFERENCE = "src/verify/groovy/datazeus/datamodeling/series1/reference-schema.sql"

    /** One of the lesson's scripts, rewritten into this spec's own schema. */
    private static String script(String name) {
        mine(new File("${SCRIPTS}/${name}.sql").text)
    }

    private static String reference() { new File(REFERENCE).text }

    /** Comments out, the trailing semicolon off, and `practice` renamed to this spec's schema. */
    private static String mine(String sql) {
        sql.readLines().collect { it.replaceFirst(/--.*$/, "") }.join("\n")
                .replaceAll(/\bpractice\b/, SCHEMA).trim().replaceFirst(/;\s*$/, "")
    }

    private void fresh(String engine) {
        sqlFor(engine).execute("DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE".toString())
        sqlFor(engine).execute("CREATE SCHEMA ${SCHEMA}".toString())
    }

    /** The reference (or a broken copy of it), statement by statement, the way the checks apply a
     *  learner's file. Its own DROP / CREATE SCHEMA lines make it a clean rebuild every time. */
    private void applyReference(String engine, String model) {
        mine(model).split(";").findAll { it.trim() }.each { sqlFor(engine).execute(it.trim()) }
    }

    /** null when the statement went through; otherwise what the engine said. */
    private Map refusal(String engine, String sql) {
        try {
            sqlFor(engine).execute(sql)
            return null
        } catch (SQLException e) {
            def server = e.respondsTo("getServerErrorMessage") ? e.serverErrorMessage : null
            return [state: e.SQLState, message: server?.message ?: e.message, detail: server?.detail]
        }
    }

    /**
     * Run ONE check from the checks file the way the check runs it: every ''' literal but the last
     * is setup, the last is judged by the assertion the check makes. True when the check passes.
     */
    private boolean verdict(String engine, String title) {
        def body = checkBody(title)
        def sqls = (body =~ /(?s)'''(.*?)'''/).collect { mine(it[1]) }
        assert sqls: "check '${title}' has no SQL"
        sqls.init().each { setup -> setup.split(";").findAll { it.trim() }.each { sqlFor(engine).execute(it.trim()) } }
        def judged = sqls.last()
        def count = (body =~ /shouldReturn\s+(\d+)\s*,/)
        if (count.find()) {
            def got = sqlFor(engine).firstRow(judged)?.values()?.first()
            return got != null && (got as long) == (count.group(1) as long)
        }
        if (body.contains("shouldReject")) return refusal(engine, judged) != null
        if (body.contains("shouldAccept")) return refusal(engine, judged) == null
        throw new IllegalStateException("check '${title}' makes no assertion this spec understands")
    }

    /** One episode's section of the checks file, from its banner to the next episode's. */
    private static String section(String n) {
        def src = new File(CHECKS).text
        int at = src.indexOf("// ── Episode ${n} ·")
        assert at >= 0: "no Episode ${n} section in the checks file"
        int next = src.indexOf("// ── Episode ", at + 1)
        next < 0 ? src.substring(at) : src.substring(at, next)
    }

    /** One check's source, from `def "title"()` to the next check. */
    private static String checkBody(String title) {
        def src = new File(CHECKS).text
        int at = src.indexOf('def "' + title + '"()')
        assert at >= 0: "no check titled '${title}' in the checks file — it was renamed or removed"
        int next = src.indexOf('\n    def "', at + 1)
        int banner = src.indexOf("\n    // ── Episode ", at + 1)
        int end = [next, banner].findAll { it >= 0 }.min() ?: src.length()
        src.substring(at, end)
    }
}
