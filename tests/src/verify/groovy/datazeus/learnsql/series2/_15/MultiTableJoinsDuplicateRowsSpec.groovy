package datazeus.learnsql.series2._15

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 2 · lesson _15
 * "Multi-Table JOINs & Grain — When a Join Silently Drops or Multiplies Your Rows".
 *
 * Every figure the video, the article, the short and the koans put in front of a learner is
 * asserted here, on BOTH engines. The lesson's scripts, and the sections that run them:
 *
 *    june-units-left-then-inner, june-units-left-all-the-way,
 *    june-units-narrowed-first, products-without-june-sale                    §1 a join that drops rows
 *    sales-and-freight-per-customer, freight-per-customer,
 *    folk-och-fa-orders-and-lines, company-freight-two-ways                    §2 a join that multiplies money
 *    sum-distinct-freight, sum-distinct-shared-freight                         §3 SUM(DISTINCT) is luck
 *    sales-and-freight-aggregated-first, chai-lines-and-customers              §4 the cure, and the bridge
 *
 * §5 asserts the data facts the lesson leans on and every number the KOANS' comments state, and
 * §6 runs the koans file itself, as written, on both engines (ported from Series 1 · 50 §8).
 *
 * ── THE EARN, AS ARITHMETIC ────────────────────────────────────────────────────────────────
 * Folk och fä HB: 87.33 × 3 + 82.08 × 2 + 77.83 × 3 = 659.64, against 247.24 over "Orders" alone.
 * The company: 3988.52 against 9871.56. SUM(DISTINCT) gives 3988.52 only because all 79 freights
 * are different — asserted, because the lesson calls it luck and the luck has to be real.
 *
 * ── ORDER AND NAMES ────────────────────────────────────────────────────────────────────────
 * The product lists are ordered by "ProductName"; none of the twenty names differ only by case or
 * punctuation, so byte order (DuckDB) and collation (PostgreSQL) agree — asserted by running the list
 * on both. The top-five lists are ordered by money, all values different.
 */
class MultiTableJoinsDuplicateRowsSpec extends NorthwindGateSpec {

    // --- 1. A JOIN THAT DROPS ROWS ------------------------------------------------------------------

    @Unroll
    def "[#engine] LEFT JOIN then JOIN for June: 6 rows of 20 products"() {
        given:
        def rows = sqlFor(engine).rows(script("june-units-left-then-inner"))

        expect: "six-rows' card"
        rows.collect { [it.ProductName, it["June units"] as int] } ==
                [["Aniseed Syrup", 3], ["Boston Crab Meat", 8], ["Chai", 12], ["Chang", 10],
                 ["Chef Antons Cajun Seasoning", 2], ["Scottish Longbreads", 5]]
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] LEFT all the way: 20 rows, Chai 79, and the units add up to 2070 — June's real total is 40"() {
        given:
        def rows = sqlFor(engine).rows(script("june-units-left-all-the-way"))
        def units = rows.collectEntries { [(it.ProductName): it["June units"] as int] }

        expect: "looks-repaired's card"
        rows.size() == 20
        rows.take(5).collect { [it.ProductName, it["June units"] as int] } ==
                [["Aniseed Syrup", 101], ["Boston Crab Meat", 128], ["Camembert Pierrot", 70], ["Chai", 79], ["Chang", 114]]
        rows.takeRight(2).collect { [it.ProductName, it["June units"] as int] } ==
                [["Tofu", 143], ["Uncle Bobs Organic Dried Pears", 84]]
        units.values().sum() == 2070

        and: "2070 is every unit ever sold — the June test in the ON did not filter a single line"
        sqlFor(engine).firstRow('SELECT sum("Quantity") AS n FROM "Order Details"').n == 2070

        and: "the product names sort alike on both engines (pinned by the full list)"
        rows*.ProductName == ["Aniseed Syrup", "Boston Crab Meat", "Camembert Pierrot", "Chai", "Chang",
                              "Chef Antons Cajun Seasoning", "Filo Mix", "Genen Shouyu", "Gnocchi di nonna Alice",
                              "Gorgonzola Telino", "Guarana Fantastica", "Ikura", "Mishi Kobe Niku", "Pavlova",
                              "Queso Cabrales", "Ravioli Angelo", "Scottish Longbreads", "Thuringer Rostbratwurst",
                              "Tofu", "Uncle Bobs Organic Dried Pears"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] narrowed first: 20 rows, 6 with June units, totalling 40, and 14 without a June sale"() {
        given:
        def rows = sqlFor(engine).rows(script("june-units-narrowed-first"))
        def sold = rows.findAll { it["June units"] != null }

        expect: "twenty-six-forty's card"
        rows.size() == 20
        rows.take(7).collect { [it.ProductName, it["June units"] == null ? null : (it["June units"] as int)] } ==
                [["Aniseed Syrup", 3], ["Boston Crab Meat", 8], ["Camembert Pierrot", null], ["Chai", 12],
                 ["Chang", 10], ["Chef Antons Cajun Seasoning", 2], ["Filo Mix", null]]
        rows.last().ProductName == "Uncle Bobs Organic Dried Pears"
        rows.last()["June units"] == null
        sold.size() == 6
        sold.collect { it["June units"] as int }.sum() == 40

        and: "the hands-on: 14"
        (sqlFor(engine).firstRow(script("products-without-june-sale"))["No June sale"] as int) == 14

        and: "the same 6 products and units as the left-then-inner query"
        sold.collect { [it.ProductName, it["June units"] as int] } ==
                sqlFor(engine).rows(script("june-units-left-then-inner")).collect { [it.ProductName, it["June units"] as int] }

        where:
        engine << ENGINES
    }

    // --- 2. A JOIN THAT MULTIPLIES MONEY --------------------------------------------------------------

    @Unroll
    def "[#engine] joined: Folk och fä HB first on 659.64 — over Orders alone Frankenversand leads on 268.33"() {
        given:
        def joined = sqlFor(engine).rows(script("sales-and-freight-per-customer"))
        def alone = sqlFor(engine).rows(script("freight-per-customer"))

        expect: "freight-top's card"
        joined.collect { [it.CompanyName, dec(it["Total sales"]), dec(it.Freight)] } == [
                ["Folk och fä HB", dec("1594.23"), dec("659.64")],
                ["Frankenversand", dec("4267.27"), dec("625.77")],
                ["Alfreds Futterkiste", dec("3838.43"), dec("603.21")],
                ["Du monde entier", dec("1918.93"), dec("547.16")],
                ["Ernst Handel", dec("3902.78"), dec("527.35")]]

        and: "freight-orders-only's card"
        alone.collect { [it.CompanyName, dec(it.Freight)] } == [
                ["Frankenversand", dec("268.33")], ["Alfreds Futterkiste", dec("253.73")],
                ["Folk och fä HB", dec("247.24")], ["Ernst Handel", dec("226.15")], ["Du monde entier", dec("205.06")]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] why: Folk och fä HB's orders carry 87.33, 82.08, 77.83 on 3, 2, 3 lines — 659.64"() {
        given:
        def rows = sqlFor(engine).rows(script("folk-och-fa-orders-and-lines"))

        expect: "why-multiplied's card"
        rows.collect { [it.OrderID, dec(it.Freight), it.Lines as int] } ==
                [[19, dec("87.33"), 3], [44, dec("82.08"), 2], [69, dec("77.83"), 3]]

        and: "the arithmetic Mnemosyne says, and the 247.24 it should have been"
        rows.collect { new BigDecimal(it.Freight.toString()) * (it.Lines as int) }.sum().stripTrailingZeros() == dec("659.64")
        rows.collect { new BigDecimal(it.Freight.toString()) }.sum().stripTrailingZeros() == dec("247.24")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the company: 3988.52 over Orders, 9871.56 after the join — 'nearly two and a half times'"() {
        given:
        def r = sqlFor(engine).firstRow(script("company-freight-two-ways"))

        expect:
        dec(r["Orders alone"]) == dec("3988.52")
        dec(r["After the join"]) == dec("9871.56")
        (new BigDecimal("9871.56") / new BigDecimal("3988.52")) > 2.4
        (new BigDecimal("9871.56") / new BigDecimal("3988.52")) < 2.5

        where:
        engine << ENGINES
    }

    // --- 3. SUM(DISTINCT) IS LUCK --------------------------------------------------------------------

    @Unroll
    def "[#engine] SUM(DISTINCT) gives 3988.52 because all 79 freights differ — and 35.5 against 45.5 when two orders share one"() {
        expect: "distinct-is-luck's card"
        dec(sqlFor(engine).firstRow(script("sum-distinct-freight"))["SUM(DISTINCT)"]) == dec("3988.52")

        and: "the luck, asserted: 79 orders, 79 different freight values"
        sqlFor(engine).firstRow('SELECT count(*) AS n, count(DISTINCT "Freight") AS d FROM "Orders"').with { [it.n, it.d] } == [79, 79]

        and: "shared-freight's card"
        def shared = sqlFor(engine).firstRow(script("sum-distinct-shared-freight"))
        dec(shared["SUM(DISTINCT)"]) == dec("35.5")
        dec(shared["Real total"]) == dec("45.5")

        where:
        engine << ENGINES
    }

    // --- 4. THE CURE, AND THE BRIDGE -------------------------------------------------------------------

    @Unroll
    def "[#engine] aggregated first: Frankenversand first again on 268.33, and every sales total unchanged"() {
        given:
        def cure = sqlFor(engine).rows(script("sales-and-freight-aggregated-first"))
        def joined = sqlFor(engine).rows(script("sales-and-freight-per-customer").replace("LIMIT 5;", ""))
                .collectEntries { [(it.CompanyName): dec(it["Total sales"])] }

        expect: "cure-result's card"
        cure.collect { [it.CompanyName, dec(it["Total sales"]), dec(it.Freight)] } == [
                ["Frankenversand", dec("4267.27"), dec("268.33")],
                ["Alfreds Futterkiste", dec("3838.43"), dec("253.73")],
                ["Folk och fä HB", dec("1594.23"), dec("247.24")],
                ["Ernst Handel", dec("3902.78"), dec("226.15")],
                ["Du monde entier", dec("1918.93"), dec("205.06")]]

        and: "'every sales total is exactly what it was' — the joined report's sales were never wrong"
        cure.every { joined[it.CompanyName] == dec(it["Total sales"]) }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the bridge: Chai on 10 lines, to 8 customers"() {
        expect:
        sqlFor(engine).firstRow(script("chai-lines-and-customers")).with { [it.Lines as int, it.Customers as int] } == [10, 8]
        sqlFor(engine).firstRow('SELECT "ProductName" AS n FROM "Products" WHERE "ProductID" = 1').n == "Chai"

        where:
        engine << ENGINES
    }

    // --- 5. THE DATA FACTS, AND WHAT THE KOANS STAND ON -----------------------------------------------

    @Unroll
    def "[#engine] no orphans, and every order has 1, 2 or 3 lines"() {
        // The episode never promises orphan rows: rows go missing only by narrowing a match.
        expect:
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Customers" c
                                   WHERE NOT EXISTS (SELECT 1 FROM "Orders" o WHERE o."CustomerID" = c."CustomerID")''').n == 0
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders" o
                                   WHERE NOT EXISTS (SELECT 1 FROM "Order Details" d WHERE d."OrderID" = o."OrderID")''').n == 0
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Products" p
                                   WHERE NOT EXISTS (SELECT 1 FROM "Order Details" d WHERE d."ProductID" = p."ProductID")''').n == 0
        sqlFor(engine).rows('''SELECT DISTINCT n FROM (SELECT count(*) AS n FROM "Order Details" GROUP BY "OrderID") t ORDER BY n''')
                .collect { it.n as int } == [1, 2, 3]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the koan comments' facts are true"() {
        expect: "koan 1: Janet took 27 orders"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "EmployeeID" = 3').n == 27

        and: "koan 2: the LEFT, LEFT, JOIN version for January 2024 returns 6 rows"
        sqlFor(engine).rows('''SELECT c."CategoryName" FROM "Categories" c
                               LEFT JOIN "Products" p ON p."CategoryID" = c."CategoryID"
                               LEFT JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                               JOIN "Orders" o ON o."OrderID" = d."OrderID"
                                AND o."OrderDate" >= '2024-01-01' AND o."OrderDate" < '2024-02-01'
                               GROUP BY c."CategoryName"''').size() == 6

        and: "koan 3: January 2024's real total is 164 units"
        sqlFor(engine).firstRow('''SELECT sum(d."Quantity") AS n FROM "Order Details" d
                                   JOIN "Orders" o ON o."OrderID" = d."OrderID"
                                   WHERE o."OrderDate" >= '2024-01-01' AND o."OrderDate" < '2024-02-01' ''').n == 164

        and: "koan 4: the shippers' freight over Orders alone"
        sqlFor(engine).rows('''SELECT s."CompanyName" AS c, SUM(o."Freight") AS f FROM "Shippers" s
                               JOIN "Orders" o ON o."ShipVia" = s."ShipperID" GROUP BY s."CompanyName" ORDER BY f DESC''')
                .collect { [it.c, dec(it.f)] } == [["Federal Shipping", dec("1338.78")], ["Speedy Express", dec("1335.32")], ["United Package", dec("1314.42")]]

        and: "koan 6: SUM(DISTINCT) over the typed-in lines returns 50.40"
        dec(sqlFor(engine).firstRow('''SELECT SUM(DISTINCT "Freight") AS s
                                       FROM (VALUES (101, 32.00), (101, 32.00), (102, 32.00), (102, 32.00),
                                                    (103, 18.40), (103, 18.40)) AS t("OrderID", "Freight")''').s) == dec("50.40")

        and: "koan 9: Nancy's freight summed after joining the lines is 3458.45"
        dec(sqlFor(engine).firstRow('''SELECT SUM(o."Freight") AS f FROM "Orders" o
                                       JOIN "Order Details" d ON d."OrderID" = o."OrderID" WHERE o."EmployeeID" = 1''').f) == dec("3458.45")
        sqlFor(engine).firstRow('SELECT "FirstName" AS n FROM "Employees" WHERE "EmployeeID" = 1').n == "Nancy"

        and: "koan 10: counting products after joining the lines gives Beverages 30"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Categories" c
                                   JOIN "Products" p ON p."CategoryID" = c."CategoryID"
                                   JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                   WHERE c."CategoryName" = 'Beverages' ''').n == 30

        where:
        engine << ENGINES
    }

    // --- 6. THE KOAN FILE ITSELF, RUN AS WRITTEN (Series 1 · 50 §8) --------------------------------

    @Unroll
    def "[#engine] koan file, as written: '#title' runs and returns exactly what it claims"() {
        given:
        def sql = koanSql(title)
        def expected = koanExpected(title)

        expect:
        resultOf(engine, sql, expected) == expected

        where:
        [engine, title] << [ENGINES, ANSWERS.keySet() as List].combinations()
    }

    def "the koans file matches the video's koans slide: ten koans, its three names, two whole queries last"() {
        given:
        def titles = (koansSource() =~ /(?m)^    def "(.+?)"\(\)/).collect { it[1] }

        expect:
        titles == (ANSWERS.keySet() as List)
        titles.size() == 10
        titles[0] == "count the rows after the join: Janet's orders become lines"
        titles[1] == "diagnose: an INNER JOIN after a LEFT JOIN drops categories"
        titles[2] == "predict: LEFT all the way looks repaired"
        koanQueries(titles[8])*.trim() == ["___"]
        koanQueries(titles[9])*.trim() == ["___"]
    }

    def "the video's editor mock quotes koan 1 verbatim, on the line its caret names"() {
        // ED_CODE, ED_CARET ("Ln : 86   Col : 39") and animFill hard-code koan 1's blank line.
        given:
        def lines = koansSource().split("\n")

        expect:
        koanBody("count the rows after the join: Janet's orders become lines").contains('JOIN "Order Details" d ON ___')
        lines[85].indexOf("___") == 38
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static String script(String name) {
        new File("../courses/learnsql/series2-intermediate/15-multi-table-joins-duplicate-rows/scripts/${name}.sql").text
    }

    private static String koansSource() {
        new File("src/koans/groovy/datazeus/learnsql/series2/_15/MultiTableJoinsDuplicateRowsKoans.groovy").text
    }

    private static String koanBody(String title) {
        def src = koansSource()
        int at = src.indexOf('def "' + title + '"()')
        assert at >= 0: "no koan titled '${title}' in the koans file — it was renamed or removed"
        int next = src.indexOf('\n    def "', at + 1)
        next < 0 ? src.substring(at) : src.substring(at, next)
    }

    private static List<String> koanQueries(String title) {
        (koanBody(title) =~ /(?s)'''(.*?)'''/).collect { it[1] }
    }

    /** THE INTENDED ANSWER FOR EACH BLANK, in koan order. */
    private static final Map<String, List<String>> ANSWERS = [
            "count the rows after the join: Janet's orders become lines"            : ['d."OrderID" = o."OrderID"'],
            "diagnose: an INNER JOIN after a LEFT JOIN drops categories"            : ["o.\"OrderDate\" >= '2024-01-01' AND o.\"OrderDate\" < '2024-02-01'"],
            "predict: LEFT all the way looks repaired"                              : ["LEFT"],
            "predict: what one more join does to freight per shipper"               : ['JOIN "Order Details" d ON d."OrderID" = o."OrderID"'],
            "count what you mean: orders, not lines, per employee"                  : ['count(DISTINCT o."OrderID")'],
            "SUM(DISTINCT) is luck: two orders with the same freight"               : ["DISTINCT"],
            "aggregate first: freight and lines per shipper"                        : ['d."OrderID" = o."OrderID"'],
            "through the bridge: lines and customers for Chang"                     : ['o."CustomerID"'],
            "write the whole query: orders, freight and sales per employee"         : ['''
                SELECT e."FirstName", f."Orders", f."Freight", s."Sales"
                FROM "Employees" e
                JOIN (SELECT "EmployeeID", count(*) AS "Orders", SUM("Freight") AS "Freight"
                      FROM "Orders" GROUP BY "EmployeeID") AS f
                  ON f."EmployeeID" = e."EmployeeID"
                JOIN (SELECT o."EmployeeID",
                             ROUND(SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")), 2) AS "Sales"
                      FROM "Orders" o JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                      GROUP BY o."EmployeeID") AS s
                  ON s."EmployeeID" = e."EmployeeID"
                ORDER BY e."FirstName"
            '''],
            "write the whole query: products and December 2022 units per category" : ['''
                SELECT c."CategoryName", p."Products", u."Units"
                FROM "Categories" c
                JOIN (SELECT "CategoryID", count(*) AS "Products" FROM "Products" GROUP BY "CategoryID") AS p
                  ON p."CategoryID" = c."CategoryID"
                LEFT JOIN (SELECT pr."CategoryID", sum(d."Quantity") AS "Units"
                           FROM "Products" pr
                           JOIN "Order Details" d ON d."ProductID" = pr."ProductID"
                           JOIN "Orders" o ON o."OrderID" = d."OrderID"
                           WHERE o."OrderDate" >= '2022-12-01' AND o."OrderDate" < '2023-01-01'
                           GROUP BY pr."CategoryID") AS u
                  ON u."CategoryID" = c."CategoryID"
                ORDER BY c."CategoryName"
            '''],
    ]

    private static String koanSql(String title) {
        def qs = koanQueries(title)
        assert qs.size() == 1: "koan '${title}' has ${qs.size()} queries; this helper expects one"
        def sql = qs[0]
        def answers = ANSWERS[title]
        int found = sql.count("___")
        assert found == answers.size(): "koan '${title}' has ${found} blank(s) but the answer table has ${answers.size()}"
        answers.each { a -> sql = sql.replaceFirst(/___/, java.util.regex.Matcher.quoteReplacement(a)) }
        sql
    }

    private static Object koanExpected(String title) {
        def body = koanBody(title)
        def rows = (body =~ /(?s)shouldReturn\(\s*(\[.*?\])\s*,\s*'''/)
        if (rows.find()) {
            return (Eval.me(rows.group(1)) as List).collect { row -> (row as List).collect { plain(it) } }
        }
        def single = (body =~ /shouldReturn\s+(-?\d+(?:\.\d+)?)\s*,\s*'''/)
        assert single.find(): "koan '${title}' declares neither rows nor a single value with shouldReturn"
        plain(new BigDecimal(single.group(1)))
    }

    private Object resultOf(String engine, String sql, Object expected) {
        if (expected instanceof List) {
            return sqlFor(engine).rows(sql).collect { r -> (0..<r.size()).collect { i -> plain(r.getAt(i)) } }
        }
        def row = sqlFor(engine).firstRow(sql)
        row == null ? null : plain(row.getAt(0))
    }

    private static Object plain(Object v) {
        v == null ? null : (v instanceof Number ? dec(v) : v)
    }

    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }
}
