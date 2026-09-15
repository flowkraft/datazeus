package datazeus.datamodeling.series1._20

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

import java.sql.SQLException

/**
 * VERIFIED spec = the PUBLISH GATE for Data Modeling · Series 1 · lesson 20
 * "Many-to-Many & Junction Tables — When the Link Is the Real Thing".
 *
 * Every figure, statement and refusal the video, the article, the trailer, the Short and the checks
 * put in front of a learner is asserted here, on BOTH engines. The lesson's scripts, in its order:
 *
 *    1. build-order-lists                  — practice."Order Lists": 79 orders, products as text   §1
 *    2. first-order-lists                  — 1,6 · 3,5 · 1,2,4 · 1,2                                §1
 *    3. chai-by-list                       — LIKE '%1%': 53                                          §1
 *    4. chai-by-order-lines                — the truth: 10                                           §1
 *    5. product-numbers-with-a-1           — 11 (1 and 10–19)                                        §1
 *    6. order-line-pairs                   — 193 lines, 193 pairs                                    §2
 *    7. products-and-suppliers             — 20 products, 6 suppliers                                §2
 *    8. create-product-suppliers           — the junction, keyed by the pair, with a price           §3
 *    9. load-todays-suppliers              — 20                                                       §3
 *   10. tokyo-traders-products             — 5 before, 6 after                                        §3
 *   11. add-a-second-supplier-for-chai     — (1, 4, 17) accepted                                      §3
 *   12. add-the-same-supplier-again        — REFUSED: Key ("ProductID", "SupplierID")=(1, 4)          §3
 *   13. products-with-several-suppliers    — product 1, with 2                                        §3
 *
 * §4 holds the scripts to the course's conventions, §5 runs the CHECKS FILE's section 20 against the
 * reference solution, and §6 proves those checks can FAIL on a broken model.
 *
 * ── A SCHEMA OF ITS OWN ─────────────────────────────────────────────────────────────────
 * NorthwindEngines hands every spec the SAME DuckDB copy and PostgreSQL; this lesson WRITES, so
 * everything it builds lives in `dm_s1_20` (every `practice` rewritten) and is dropped in cleanupSpec.
 */
class ManyToManySpec extends NorthwindGateSpec {

    static final String SCHEMA = "dm_s1_20"

    def cleanupSpec() {
        ENGINES.each { e ->
            ["probe_customer", "probe_line", "probe_shipper", "probe_supply"].each { t -> sqlFor(e).execute("DROP TABLE IF EXISTS ${t}".toString()) }
            sqlFor(e).execute("DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE".toString())
        }
    }

    // --- 1. The list in a column, and the answer it gives ------------------------------------

    @Unroll
    def "[#engine] the list table holds 79 orders; the first lists are 1,6 · 3,5 · 1,2,4 · 1,2"() {
        given:
        fresh(engine)

        when:
        sqlFor(engine).execute(script("build-order-lists"))

        then:
        (sqlFor(engine).firstRow("SELECT count(*) AS n FROM ${SCHEMA}.\"Order Lists\"".toString()).n as int) == 79
        sqlFor(engine).rows(script("first-order-lists")).collect { [it.OrderID as int, it.Products] } ==
                [[1, "1,6"], [2, "3,5"], [3, "1,2,4"], [4, "1,2"]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the list says 53 orders contain Chai; the order lines say 10; 11 product numbers contain a 1"() {
        given:
        fresh(engine)
        sqlFor(engine).execute(script("build-order-lists"))

        expect:
        (sqlFor(engine).firstRow(script("chai-by-list")).values().first() as int) == 53
        (sqlFor(engine).firstRow(script("chai-by-order-lines")).values().first() as int) == 10
        (sqlFor(engine).firstRow(script("product-numbers-with-a-1")).values().first() as int) == 11
        and: "the eleven are 1 and 10 to 19, as the slide lists them"
        sqlFor(engine).rows('''SELECT "ProductID" FROM "Products" WHERE CAST("ProductID" AS VARCHAR) LIKE '%1%' ORDER BY 1''')
                .collect { it.ProductID as int } == [1] + (10..19).toList()
        and: "the three facts sewn on in the trailer are order 1, product 1's"
        factsOfOrder1Product1(engine) == [3, dec("18"), dec("0.05")]

        where:
        engine << ENGINES
    }

    // --- 2. The honest junction and the one-to-many column -----------------------------------

    @Unroll
    def "[#engine] Order Details: 193 lines and 193 pairs; Products: 20 products over 6 suppliers"() {
        when:
        def pairs = sqlFor(engine).firstRow(script("order-line-pairs"))
        def ps = sqlFor(engine).firstRow(script("products-and-suppliers"))

        then:
        (pairs["Order lines"] as int) == 193 && (pairs.Pairs as int) == 193
        (ps.Products as int) == 20 && (ps.Suppliers as int) == 6

        where:
        engine << ENGINES
    }

    // --- 3. Product Suppliers: build, load, extend, refuse, answer -----------------------------

    @Unroll
    def "[#engine] product suppliers: 20 load, Tokyo Traders 5, Chai gets a second supplier, the pair again is refused, 1 product has 2"() {
        given:
        fresh(engine)
        sqlFor(engine).execute(script("create-product-suppliers"))

        when:
        int loaded = sqlFor(engine).executeUpdate(script("load-todays-suppliers"))
        int tokyoBefore = sqlFor(engine).firstRow(script("tokyo-traders-products")).values().first() as int
        def second = refusal(engine, script("add-a-second-supplier-for-chai"))
        def again = refusal(engine, script("add-the-same-supplier-again"))
        def several = sqlFor(engine).rows(script("products-with-several-suppliers"))
        int tokyoAfter = sqlFor(engine).firstRow(script("tokyo-traders-products")).values().first() as int

        then:
        loaded == 20
        tokyoBefore == 5
        second == null
        again != null
        if (engine == "postgres") {
            assert again.state == "23505"
            assert again.message == 'duplicate key value violates unique constraint "Product Suppliers_pkey"'
            assert again.detail == 'Key ("ProductID", "SupplierID")=(1, 4) already exists.'
        } else {
            assert again.message.contains('Duplicate key "ProductID: 1, SupplierID: 4" violates primary key constraint')
        }
        several.collect { [it.ProductID as int, it.Suppliers as int] } == [[1, 2]]
        tokyoAfter == 6
        and: "Chai's own supplier is Exotic Liquids (1), Tokyo Traders is supplier 4, and Chang's supplier is 1"
        sqlFor(engine).rows('SELECT "ProductID", "SupplierID" FROM "Products" WHERE "ProductID" IN (1, 2) ORDER BY 1').collect { [it.ProductID as int, it.SupplierID as int] } == [[1, 1], [2, 1]]
        sqlFor(engine).firstRow('SELECT "CompanyName" AS n FROM "Suppliers" WHERE "SupplierID" = 4').n == "Tokyo Traders"

        where:
        engine << ENGINES
    }

    // --- 4. The scripts keep the course's conventions -------------------------------------------

    def "every script reads Northwind UNQUALIFIED, writes to practice, loads in order, and stays short"() {
        given:
        def all = new File(SCRIPTS).listFiles().findAll { it.name.endsWith(".sql") }

        expect:
        all.size() == 13
        all.every { !(it.text =~ /(?i)\b(main|public)\./) }
        all.findAll { it.text =~ /(?i)\b(INSERT|CREATE)\b/ }.every { it.text.contains('practice."') }
        all.findAll { it.text =~ /(?i)INSERT[\s\S]*SELECT/ }.every { it.text =~ /(?i)ORDER BY/ }
        all.every { !it.text.contains("--") }
        all.every { f -> def l = f.text.readLines(); l.size() < 3 || l[1..-2].every { it.length() <= 45 } }
    }

    // --- 5. The checks file, section 20, run against the reference solution --------------------

    /** Episode 20's checks, in the order the file runs them. Four of the names are on screen. */
    static final List<String> CHECKS_20 = [
            "today's 20 product-supplier pairs fit your table",
            "every one of the 20 products has a supplier in your table",
            "Tokyo Traders supplies 5 products in your table",
            "the same supplier twice for one product is refused",
            "a second supplier for Chang still gets in",
    ]

    def "the checks file carries exactly episode 20's five checks, in order"() {
        expect:
        (section("20") =~ /def "(.+?)"\(\)/).collect { it[1] } == CHECKS_20
    }

    @Unroll
    def "[#engine] every episode 20 check PASSES on the reference solution"() {
        given:
        applyReference(engine, reference(), false)

        expect:
        CHECKS_20.every { verdict(engine, it) }

        where:
        engine << ENGINES
    }

    // --- 6. The checks can fail -------------------------------------------------------------------

    @Unroll
    def "[#engine] '#check' FAILS on a model that #flaw"() {
        given:
        def model = reference()
        assert model.count(from) == 1: "the reference no longer contains: ${from}"
        applyReference(engine, model.replace(from, to), true)

        expect:
        CHECKS_20.takeWhile { it != check }.every { verdict(engine, it) }   // the checks before it still pass
        !verdict(engine, check)

        where:
        [engine, check, flaw, from, to] << ENGINES.collectMany { e ->
            [
                    [e, "today's 20 product-supplier pairs fit your table", "never loads today's suppliers",
                     'SELECT "ProductID", "SupplierID", "UnitPrice"\nFROM "Products"\n', 'SELECT "ProductID", "SupplierID", "UnitPrice"\nFROM "Products"\nWHERE 1 = 0\n'],
                    [e, "Tokyo Traders supplies 5 products in your table", "stores the category where the supplier belongs",
                     'SELECT "ProductID", "SupplierID", "UnitPrice"\n', 'SELECT "ProductID", "CategoryID", "UnitPrice"\n'],
                    [e, "the same supplier twice for one product is refused", "gives the junction no key",
                     '  "SupplierPrice" DECIMAL(19,4),\n  PRIMARY KEY ("ProductID", "SupplierID")\n', '  "SupplierPrice" DECIMAL(19,4)\n'],
                    [e, "a second supplier for Chang still gets in", "keys the junction by the product alone",
                     '  PRIMARY KEY ("ProductID", "SupplierID")\n', '  PRIMARY KEY ("ProductID")\n'],
            ]
        }
    }

    private List factsOfOrder1Product1(String engine) {
        def r = sqlFor(engine).firstRow('SELECT "Quantity" AS q, "UnitPrice" AS p, "Discount" AS d FROM "Order Details" WHERE "OrderID" = 1 AND "ProductID" = 1')
        [r.q as int, dec(r.p), dec(r.d)]
    }

    // --- helpers ------------------------------------------------------------------------------------

    static final String SCRIPTS = "../courses/datamodeling/series1-modeling-fundamentals/20-many-to-many-and-junction-tables/scripts"
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
