package datazeus.datamodeling.series1._15

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

import java.sql.SQLException

/**
 * VERIFIED spec = the PUBLISH GATE for Data Modeling · Series 1 · lesson 15
 * "Grain & Keys — One Row per What, and What Makes It Unique".
 *
 * Every figure, statement and refusal the video, the article, the trailer, the Short and the checks
 * put in front of a learner is asserted here, on BOTH engines. The lesson's scripts, in its order:
 *
 *    1. order-1-lines                       — order 1 holds TWO products: 1 and 6                  §1
 *    2. create-order-lines-keyed-by-order   — Leo's guess, PRIMARY KEY ("OrderID")                 §2
 *    3. load-order-lines                    — REFUSED on key 1 (the ORDER BY makes it 1 on both)   §2
 *    4. count-your-order-lines              — 0: the refused load left nothing behind              §2
 *    5. drop-order-lines                    — the rebuild                                           §3
 *    6. create-order-lines-keyed-by-pair    — PRIMARY KEY ("OrderID", "ProductID")                 §3
 *    7. load-order-lines                    — 193 load                                              §3
 *    8. count-the-pairs                     — 193 rows, 193 pairs                                   §3
 *    9. insert-product-1-on-order-1-again   — REFUSED: Key ("OrderID", "ProductID")=(1, 1)          §3
 *   10. insert-product-1-on-order-2         — accepted: 194                                         §3
 *   11. candidate-keys                      — 25 customers, 25 ids, 25 names                        §4
 *   12. customer-keys · product-keys        — ALFKI … and 1 · 2 · 3                                  §4
 *
 * §5 holds the scripts to the course's conventions, §6 runs the CHECKS FILE's section 15 against the
 * reference solution, and §7 proves those checks can FAIL on a broken model.
 *
 * ── A SCHEMA OF ITS OWN ─────────────────────────────────────────────────────────────────
 * NorthwindEngines hands every spec the SAME DuckDB copy and PostgreSQL; this lesson WRITES, so
 * everything it builds lives in `dm_s1_15` (every `practice` rewritten) and is dropped in cleanupSpec.
 */
class GrainAndKeysSpec extends NorthwindGateSpec {

    static final String SCHEMA = "dm_s1_15"

    def cleanupSpec() {
        ENGINES.each { e ->
            ["probe_customer", "probe_line", "probe_shipper"].each { t -> sqlFor(e).execute("DROP TABLE IF EXISTS ${t}".toString()) }
            sqlFor(e).execute("DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE".toString())
        }
    }

    // --- 1. What one row of the order lines is ----------------------------------------------

    @Unroll
    def "[#engine] order 1 holds two lines: product 1 at 18, qty 3, 0.05 off — and product 6 at 18.4, qty 5"() {
        expect:
        sqlFor(engine).rows(script("order-1-lines")).collect { [it.OrderID as int, it.ProductID as int, dec(it.UnitPrice), it.Quantity as int, dec(it.Discount)] } ==
                [[1, 1, dec("18"), 3, dec("0.05")], [1, 6, dec("18.4"), 5, dec("0")]]

        where:
        engine << ENGINES
    }

    // --- 2. Keyed by the order: the real rows refuse ------------------------------------------

    @Unroll
    def "[#engine] PRIMARY KEY (OrderID) refuses the real order lines on key 1, and nothing is loaded"() {
        given:
        fresh(engine)
        sqlFor(engine).execute(script("create-order-lines-keyed-by-order"))

        when:
        def refused = refusal(engine, script("load-order-lines"))

        then:
        refused != null
        if (engine == "postgres") {
            assert refused.state == "23505"
            assert refused.message == 'duplicate key value violates unique constraint "Order Details_pkey"'
            assert refused.detail == 'Key ("OrderID")=(1) already exists.'
        } else {
            assert refused.message.contains('PRIMARY KEY or UNIQUE constraint violated: duplicate key "1"')
        }
        (sqlFor(engine).firstRow(script("count-your-order-lines")).values().first() as int) == 0

        where:
        engine << ENGINES
    }

    // --- 3. Keyed by the pair: everything fits, and the pair is guarded -------------------------

    @Unroll
    def "[#engine] keyed by the pair: 193 load, 193 pairs, (1, 1) again refused, (2, 1) accepted — 194"() {
        given:
        fresh(engine)
        sqlFor(engine).execute(script("create-order-lines-keyed-by-order"))
        refusal(engine, script("load-order-lines"))
        sqlFor(engine).execute(script("drop-order-lines"))
        sqlFor(engine).execute(script("create-order-lines-keyed-by-pair"))

        when:
        int loaded = sqlFor(engine).executeUpdate(script("load-order-lines"))
        def pairs = sqlFor(engine).firstRow(script("count-the-pairs"))
        def again = refusal(engine, script("insert-product-1-on-order-1-again"))
        def other = refusal(engine, script("insert-product-1-on-order-2"))
        def after = sqlFor(engine).firstRow(script("count-the-pairs"))

        then:
        loaded == 193
        (pairs.Rows as int) == 193 && (pairs.Pairs as int) == 193
        again != null
        if (engine == "postgres") {
            assert again.state == "23505"
            assert again.message == 'duplicate key value violates unique constraint "Order Details_pkey"'
            assert again.detail == 'Key ("OrderID", "ProductID")=(1, 1) already exists.'
        } else {
            assert again.message.contains('Duplicate key "OrderID: 1, ProductID: 1" violates primary key constraint')
        }
        other == null
        (after.Rows as int) == 194 && (after.Pairs as int) == 194
        and: "order 2 held products 3 and 5 before — so product 1 on it was new"
        sqlFor(engine).rows('SELECT "ProductID" FROM "Order Details" WHERE "OrderID" = 2 ORDER BY 1').collect { it.ProductID as int } == [3, 5]

        where:
        engine << ENGINES
    }

    // --- 4. Candidate keys and the two key styles ----------------------------------------------

    @Unroll
    def "[#engine] 25 customers with 25 ids and 25 names; the first codes and the first product numbers"() {
        when:
        def c = sqlFor(engine).firstRow(script("candidate-keys"))

        then:
        [c.Customers, c.IDs, c.Names].collect { it as int } == [25, 25, 25]
        sqlFor(engine).rows(script("customer-keys")).collect { [it.CustomerID, it.CompanyName] } ==
                [["ALFKI", "Alfreds Futterkiste"], ["ANATR", "Ana Trujillo Emparedados y helados"], ["ANTON", "Antonio Moreno Taquería"]]
        sqlFor(engine).rows(script("product-keys")).collect { [it.ProductID as int, it.ProductName] } ==
                [[1, "Chai"], [2, "Chang"], [3, "Aniseed Syrup"]]
        and: "no Northwind customer code starts with NW, so the checks' NWCO1 is genuinely new"
        (sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Customers" WHERE "CustomerID" LIKE 'NW%' ''').n as int) == 0

        where:
        engine << ENGINES
    }

    // --- 5. The scripts keep the course's conventions -------------------------------------------

    def "every script reads Northwind UNQUALIFIED, writes to practice, loads in order, and stays short"() {
        given:
        def all = new File(SCRIPTS).listFiles().findAll { it.name.endsWith(".sql") }

        expect:
        all.size() == 12
        all.every { !(it.text =~ /(?i)\b(main|public)\./) }
        all.findAll { it.text =~ /(?i)\b(INSERT|CREATE|DROP)\b/ }.every { it.text.contains('practice."') }
        all.findAll { it.text =~ /(?i)INSERT[\s\S]*SELECT/ }.every { it.text =~ /(?i)ORDER BY/ }
        all.every { !it.text.contains("--") }
        all.every { f -> def l = f.text.readLines(); l.size() < 3 || l[1..-2].every { it.length() <= 45 } }
    }

    // --- 6. The checks file, section 15, run against the reference solution --------------------

    /** Episode 15's checks, in the order the file runs them. Four of the names are on screen. */
    static final List<String> CHECKS_15 = [
            "Northwind's 25 customers fit your table",
            "Northwind's 79 orders fit your table",
            "Northwind's 20 products fit your table",
            "Northwind's 6 suppliers fit your table",
            "Northwind's 3 employees fit your table",
            "Northwind's 193 order lines fit your table",
            "a second customer ALFKI is refused",
            "a second order number 1 is refused",
            "the same product twice on one order is refused",
            "a new customer NWCO1 still gets in",
            "the same product on a different order still gets in",
    ]

    def "the checks file carries exactly episode 15's eleven checks, in order"() {
        expect:
        (section("15") =~ /def "(.+?)"\(\)/).collect { it[1] } == CHECKS_15
    }

    @Unroll
    def "[#engine] every episode 15 check PASSES on the reference solution"() {
        given:
        applyReference(engine, reference(), false)

        expect:
        CHECKS_15.every { verdict(engine, it) }

        where:
        engine << ENGINES
    }

    // --- 7. The checks can fail -------------------------------------------------------------------

    @Unroll
    def "[#engine] '#check' FAILS on a model that #flaw"() {
        given:
        def model = reference()
        assert model.count(from) == 1: "the reference no longer contains: ${from}"
        applyReference(engine, model.replace(from, to), true)

        expect:
        CHECKS_15.takeWhile { it != check }.every { verdict(engine, it) }   // the checks before it still pass
        !verdict(engine, check)

        where:
        [engine, check, flaw, from, to] << ENGINES.collectMany { e ->
            [
                    [e, "Northwind's 193 order lines fit your table", "keys the order lines by the order alone",
                     '  PRIMARY KEY ("OrderID", "ProductID")\n', '  PRIMARY KEY ("OrderID")\n'],
                    [e, "a second customer ALFKI is refused", "gives customers no key",
                     '"CustomerID"   VARCHAR PRIMARY KEY,', '"CustomerID"   VARCHAR,'],
                    [e, "a second order number 1 is refused", "gives orders no key",
                     '"OrderID"     INTEGER PRIMARY KEY,', '"OrderID"     INTEGER,'],
                    [e, "the same product twice on one order is refused", "gives the order lines no key",
                     '  "Discount"  DECIMAL(8,4),\n  PRIMARY KEY ("OrderID", "ProductID")\n', '  "Discount"  DECIMAL(8,4)\n'],
                    [e, "a new customer NWCO1 still gets in", "is stricter than the business",
                     '"CustomerID"   VARCHAR PRIMARY KEY,', '"CustomerID"   VARCHAR PRIMARY KEY CHECK ("CustomerID" NOT LIKE \'NW%\'),'],
            ]
        }
    }

    // --- helpers ------------------------------------------------------------------------------------

    static final String SCRIPTS = "../courses/datamodeling/series1-modeling-fundamentals/15-grain-and-keys/scripts"
    static final String CHECKS = "src/koans/groovy/datazeus/datamodeling/series1/NorthwindModelChecks.groovy"
    static final String REFERENCE = "src/verify/groovy/datazeus/datamodeling/series1/reference-schema.sql"

    private static String script(String name) { mine(new File("${SCRIPTS}/${name}.sql").text) }

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

    /** The reference (or a broken copy), statement by statement. A broken model may refuse its own
     *  load — `tolerant` lets that happen, exactly as a learner's file would, and the checks judge the
     *  table it left behind. */
    private void applyReference(String engine, String model, boolean tolerant) {
        mine(model).split(";").findAll { it.trim() }.each { stmt ->
            if (tolerant) refusal(engine, stmt.trim()) else sqlFor(engine).execute(stmt.trim())
        }
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

    /** Run ONE check from the checks file the way the check runs it; true when it passes. */
    private boolean verdict(String engine, String title) {
        def body = checkBody(title)
        def sqls = (body =~ /(?s)'''(.*?)'''/).collect { mine(it[1]) }
        assert sqls: "check '${title}' has no SQL"
        sqls.init().each { setup -> setup.split(";").findAll { it.trim() }.each { sqlFor(engine).execute(it.trim()) } }
        def judged = sqls.last()
        def count = (body =~ /shouldReturn\s+(\d+)\s*,/)
        if (count.find()) {
            def got
            try { got = sqlFor(engine).firstRow(judged)?.values()?.first() } catch (SQLException ignored) { return false }
            return got != null && (got as long) == (count.group(1) as long)
        }
        if (body.contains("shouldReject")) return refusal(engine, judged) != null
        if (body.contains("shouldAccept")) return refusal(engine, judged) == null
        throw new IllegalStateException("check '${title}' makes no assertion this spec understands")
    }

    private static String section(String n) {
        def src = new File(CHECKS).text
        int at = src.indexOf("// ── Episode ${n} ·")
        assert at >= 0: "no Episode ${n} section in the checks file"
        int next = src.indexOf("// ── Episode ", at + 1)
        next < 0 ? src.substring(at) : src.substring(at, next)
    }

    private static String checkBody(String title) {
        def src = new File(CHECKS).text
        int at = src.indexOf('def "' + title + '"()')
        assert at >= 0: "no check titled '${title}' in the checks file — it was renamed or removed"
        int next = src.indexOf('\n    def "', at + 1)
        int banner = src.indexOf("\n    // ── Episode ", at + 1)
        int end = [next, banner].findAll { it >= 0 }.min() ?: src.length()
        src.substring(at, end)
    }

    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }
}
