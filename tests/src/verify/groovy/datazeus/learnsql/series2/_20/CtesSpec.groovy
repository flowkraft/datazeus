package datazeus.learnsql.series2._20

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 2 · lesson _20
 * "CTEs (WITH) — Breaking a Long Query Into Named Steps".
 *
 * Every figure the video, the article, the short and the koans put in front of a learner is
 * asserted here, on BOTH engines. The lesson's scripts, and the sections that run them:
 *
 *    customers-above-the-average-customer-nested, average-customer-with,
 *    customers-above-the-average-customer-with                                §1 name it, then use it
 *    june-report-with, june-report-with-check, june-lines-step                §2 steps that read steps
 *    june-report-tidied, june-report-tidied-check                             §3 the tidy-up
 *    sales-and-freight-with, days-quiet-with, customers-by-status-with        §4 the hands-on and the through-line
 *
 * §5 asserts the numbers the KOANS' comments state, and §6 runs the koans file itself, as written,
 * on both engines — including the two EQUIVALENCE koans, whose expected rows are whatever the given
 * nested query returns — plus, on DuckDB, the comparison exactly as KoanBase makes it (Groovy ==).
 *
 * ── A REWRITE IS ONLY A REWRITE IF THE ANSWER DID NOT MOVE ────────────────────────────────
 * The WITH versions are compared ROW FOR ROW with the queries they rewrite, including the ones that
 * live in other lessons' scripts folders (Series 2 · 00's nested query, Series 2 · 15's June cure and
 * sales-and-freight cure). If either lesson's script changes, this gate fails.
 */
class CtesSpec extends NorthwindGateSpec {

    // --- 1. NAME IT, THEN USE IT -------------------------------------------------------------------

    def "the nested query on screen is the subqueries lesson's script, byte for byte"() {
        expect:
        script("customers-above-the-average-customer-nested") ==
                new File("../courses/learnsql/series2-intermediate/00-subqueries/scripts/customers-above-the-average-customer.sql").text
    }

    @Unroll
    def "[#engine] WITH customer_totals: the average customer is 2326.13, and the same 9 customers as the nested query"() {
        given:
        def nested = sqlFor(engine).rows(script("customers-above-the-average-customer-nested"))
        def with = sqlFor(engine).rows(script("customers-above-the-average-customer-with"))

        expect: "with-one's figure"
        dec(sqlFor(engine).firstRow(script("average-customer-with"))["Average customer"]) == dec("2326.13")

        and: "nine-again: row for row the nested query's answer"
        with.size() == 9
        with.collect { [it.CompanyName, dec(it["Total sales"])] } == nested.collect { [it.CompanyName, dec(it["Total sales"])] }
        with.first().CompanyName == "Cactus Comidas para llevar"
        dec(with.first()["Total sales"]) == dec("4567.8")
        with.takeRight(3).collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Morgenstern Gesundkost", dec("3289.13")], ["Around the Horn", dec("2701.58")], ["Great Lakes Food Market", dec("2683.28")]]

        and: "the nested query really is three brackets deep (the trailer's claim)"
        script("customers-above-the-average-customer-nested").count("(SELECT") == 2

        where:
        engine << ENGINES
    }

    // --- 2. STEPS THAT READ STEPS -------------------------------------------------------------------

    @Unroll
    def "[#engine] the June report as steps returns exactly last lesson's cure: 20 rows, 6 sold, 40 units"() {
        given:
        def with = sqlFor(engine).rows(script("june-report-with"))
        def cure = sqlFor(engine).rows(new File("../courses/learnsql/series2-intermediate/15-multi-table-joins-duplicate-rows/scripts/june-units-narrowed-first.sql").text)
        def check = sqlFor(engine).firstRow(script("june-report-with-check"))

        expect:
        with.collect { [it.ProductName, it["June units"] == null ? null : (it["June units"] as int)] } ==
                cure.collect { [it.ProductName, it["June units"] == null ? null : (it["June units"] as int)] }

        and: "same-answer's card"
        [check.Rows as int, check.Sold as int, check.Units as int] == [20, 6, 40]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] one step alone: six June lines, orders 4 to 7, adding up to 40"() {
        expect: "june-lines-result's card"
        sqlFor(engine).rows(script("june-lines-step")).collect { [it.OrderID as int, it.ProductID as int, it.Quantity as int] } ==
                [[4, 1, 12], [4, 2, 10], [5, 3, 3], [6, 4, 2], [6, 5, 5], [7, 6, 8]]

        where:
        engine << ENGINES
    }

    // --- 3. THE TIDY-UP ----------------------------------------------------------------------------

    @Unroll
    def "[#engine] the tidy-up: 6 rows, and the units still add up to 40 — only the row count notices"() {
        given:
        def tidied = sqlFor(engine).rows(script("june-report-tidied"))
        def check = sqlFor(engine).firstRow(script("june-report-tidied-check"))

        expect: "six-rows' card"
        tidied.collect { [it.ProductName, it["June units"] as int] } ==
                [["Aniseed Syrup", 3], ["Boston Crab Meat", 8], ["Chai", 12], ["Chang", 10],
                 ["Chef Antons Cajun Seasoning", 2], ["Scottish Longbreads", 5]]

        and: "equivalent-check's second row"
        [check.Rows as int, check.Sold as int, check.Units as int] == [6, 6, 40]

        where:
        engine << ENGINES
    }

    // --- 4. THE HANDS-ON AND THE THROUGH-LINE --------------------------------------------------------

    @Unroll
    def "[#engine] the hands-on rewrite returns last lesson's sales-and-freight cure row for row: Frankenversand 268.33 first"() {
        given:
        def with = sqlFor(engine).rows(script("sales-and-freight-with"))
        def cure = sqlFor(engine).rows(new File("../courses/learnsql/series2-intermediate/15-multi-table-joins-duplicate-rows/scripts/sales-and-freight-aggregated-first.sql").text)

        expect:
        with.collect { [it.CompanyName, dec(it["Total sales"]), dec(it.Freight)] } == cure.collect { [it.CompanyName, dec(it["Total sales"]), dec(it.Freight)] }
        with.first().CompanyName == "Frankenversand"
        dec(with.first().Freight) == dec("268.33")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] days_quiet: QUICK-Stop 199 on top, and 11 gone quiet against 14 active"() {
        given:
        def top = sqlFor(engine).rows(script("days-quiet-with"))
        def byStatus = sqlFor(engine).rows(script("customers-by-status-with"))

        expect: "gone-quiet's card"
        top.collect { [it.CompanyName, it.Days as int, it.Status] } == [
                ["QUICK-Stop", 199, "gone quiet"], ["Toms Spezialitäten", 190, "gone quiet"], ["Die Wandernde Kuh", 183, "gone quiet"],
                ["Antonio Moreno Taquería", 159, "gone quiet"], ["Blauer See Delikatessen", 138, "gone quiet"]]

        and: "its label"
        byStatus.collect { [it.Status, it.Customers as int] } == [["active", 14], ["gone quiet", 11]]

        where:
        engine << ENGINES
    }

    // --- 5. What the KOANS stand on --------------------------------------------------------------

    @Unroll
    def "[#engine] the koan comments' facts are true"() {
        expect: "koan 2: the average category is 7269.16"
        dec(sqlFor(engine).firstRow('''SELECT ROUND(AVG(t."Total"), 2) AS a FROM (
                                         SELECT p."CategoryID", SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
                                         FROM "Products" p JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                         GROUP BY p."CategoryID") t''').a) == dec("7269.16")

        and: "koan 6: the tidied version (filter in the final WHERE) returns 6 rows of 8"
        sqlFor(engine).rows('''WITH lines AS (SELECT p."CategoryID", d."Quantity", o."OrderDate"
                                             FROM "Products" p JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                             JOIN "Orders" o ON o."OrderID" = d."OrderID")
                               SELECT c."CategoryName", sum(l."Quantity") AS u FROM "Categories" c
                               LEFT JOIN lines l ON l."CategoryID" = c."CategoryID"
                               WHERE l."OrderDate" >= '2023-08-01' AND l."OrderDate" < '2023-09-01'
                               GROUP BY c."CategoryName"''').size() == 6

        and: "koan 9: ten countries, the average country is 5815.33"
        def countries = sqlFor(engine).firstRow('''SELECT count(*) AS n, ROUND(AVG(t."Total"), 2) AS a FROM (
                                     SELECT c."Country", SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
                                     FROM "Customers" c JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                                     JOIN "Order Details" d ON d."OrderID" = o."OrderID" GROUP BY c."Country") t''')
        (countries.n as int) == 10
        dec(countries.a) == dec("5815.33")

        and: "koan 10: every supplier sold something in August 2023"
        sqlFor(engine).firstRow('''SELECT count(DISTINCT p."SupplierID") AS n FROM "Products" p
                                   JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                   JOIN "Orders" o ON o."OrderID" = d."OrderID"
                                   WHERE o."OrderDate" >= '2023-08-01' AND o."OrderDate" < '2023-09-01' ''').n == 6

        where:
        engine << ENGINES
    }

    // --- 6. THE KOAN FILE ITSELF, RUN AS WRITTEN -----------------------------------------------------

    @Unroll
    def "[#engine] koan file, as written: '#title' runs and returns exactly what it claims"() {
        given:
        def sql = koanSql(title)
        def expected = koanExpected(engine, title)

        expect:
        resultOf(engine, sql, expected) == expected

        where:
        [engine, title] << [ENGINES, ANSWERS.keySet() as List].combinations()
    }

    @Unroll
    def "[duckdb, as KoanBase compares] '#title' passes the learner's own check"() {
        // KoanBase: rows(sql) as plain Lists, compared with the koan's expected value by Groovy ==.
        // A DOUBLE where the koan wrote a decimal would still pass plain() here and fail for the learner.
        given:
        def sql = koanSql(title)
        def db = sqlFor("duckdb")
        def raw = { String q -> db.rows(q).collect { r -> r.values().toList() } }
        def nested = koanNested(title)

        expect:
        nested != null ? raw(sql) == raw(nested)
                : (koanRawExpected(title) instanceof List ? raw(sql) == koanRawExpected(title)
                   : db.firstRow(sql).values().toList()[0] == koanRawExpected(title))

        where:
        title << (ANSWERS.keySet() as List)
    }

    def "the koans file matches the video's koans slide: ten koans, its three names, two equivalence koans, two whole queries last"() {
        given:
        def titles = (koansSource() =~ /(?m)^    def "(.+?)"\(\)/).collect { it[1] }

        expect:
        titles == (ANSWERS.keySet() as List)
        titles.size() == 10
        titles[0] == "name it, then use the name: the three biggest categories"
        titles[1] == "use a step twice: categories above the average category"
        titles[2] == "one step reads another: sales per order, per employee"
        titles.findAll { koanNested(it) != null } == ["equivalent: rewrite the nested supplier query with WITH",
                                                      "equivalent: rewrite the shipper report with WITH"]
        koanQueries(titles[8])*.trim() == ["___"]
        koanQueries(titles[9])*.trim() == ["___"]
    }

    def "the video's editor mock quotes koan 1 verbatim, on the line its caret names"() {
        // ED_CODE, ED_CARET ("Ln : 93   Col : 18") and animFill hard-code koan 1's blank line.
        given:
        def lines = koansSource().split("\n")

        expect:
        lines[92] == "            FROM ___"
        lines[92].indexOf("___") == 17
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static String script(String name) {
        new File("../courses/learnsql/series2-intermediate/20-ctes/scripts/${name}.sql").text
    }

    private static String koansSource() {
        new File("src/koans/groovy/datazeus/learnsql/series2/_20/CtesKoans.groovy").text
    }

    private static String koanBody(String title) {
        def src = koansSource()
        int at = src.indexOf('def "' + title + '"()')
        assert at >= 0: "no koan titled '${title}' in the koans file — it was renamed or removed"
        int next = src.indexOf('\n    def "', at + 1)
        next < 0 ? src.substring(at) : src.substring(at, next)
    }

    private static List<String> koanQueries(String title) {
        (koanBody(title) =~ /(?s)'''(.*?)'''/).collect { it[1] }.findAll { it.contains("___") }
    }

    /** An equivalence koan's given, correct, nested query — null for an ordinary koan. */
    private static String koanNested(String title) {
        def body = koanBody(title)
        if (!body.contains("shouldReturn(nested,")) return null
        def all = (body =~ /(?s)'''(.*?)'''/).collect { it[1] }
        all.find { !it.contains("___") }
    }

    /** THE INTENDED ANSWER FOR EACH BLANK, in koan order. */
    private static final Map<String, List<String>> ANSWERS = [
            "name it, then use the name: the three biggest categories"            : ["category_totals"],
            "use a step twice: categories above the average category"             : ['(SELECT AVG("Total") FROM category_totals)'],
            "one step reads another: sales per order, per employee"               : ["employee_totals"],
            "a total of totals: each supplier's share of all sales"               : ["supplier_sales"],
            "equivalent: rewrite the nested supplier query with WITH"             : ['''SELECT p."SupplierID",
                     SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
              FROM "Products" p
              JOIN "Order Details" d ON d."ProductID" = p."ProductID"
              GROUP BY p."SupplierID"'''],
            "diagnose: the tidy-up that dropped two categories"                   : ["o.\"OrderDate\" >= '2023-08-01' AND o.\"OrderDate\" < '2023-09-01'"],
            "run one step on its own"                                             : ["august_lines"],
            "equivalent: rewrite the shipper report with WITH"                    : ['''SELECT o."ShipVia", count(*) AS "Lines"
              FROM "Orders" o
              JOIN "Order Details" d ON d."OrderID" = o."OrderID"
              GROUP BY o."ShipVia"'''],
            "write the whole query: countries above the average country"          : ['''
                WITH country_totals AS (
                  SELECT c."Country",
                         SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
                  FROM "Customers" c
                  JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                  JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                  GROUP BY c."Country"
                )
                SELECT "Country", ROUND("Total", 2) AS "Total"
                FROM country_totals
                WHERE "Total" > (SELECT AVG("Total") FROM country_totals)
                ORDER BY "Total" DESC
            '''],
            "write the whole query: products and August 2023 units per supplier" : ['''
                WITH product_counts AS (
                  SELECT "SupplierID", count(*) AS "Products"
                  FROM "Products"
                  GROUP BY "SupplierID"
                ),
                august_units AS (
                  SELECT p."SupplierID", sum(d."Quantity") AS "Units"
                  FROM "Products" p
                  JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                  JOIN "Orders" o ON o."OrderID" = d."OrderID"
                  WHERE o."OrderDate" >= '2023-08-01' AND o."OrderDate" < '2023-09-01'
                  GROUP BY p."SupplierID"
                )
                SELECT s."CompanyName", pc."Products", a."Units"
                FROM "Suppliers" s
                JOIN product_counts pc ON pc."SupplierID" = s."SupplierID"
                LEFT JOIN august_units a ON a."SupplierID" = s."SupplierID"
                ORDER BY s."CompanyName"
            '''],
    ]

    private static String koanSql(String title) {
        def qs = koanQueries(title)
        assert qs.size() == 1: "koan '${title}' has ${qs.size()} queries with a blank; this helper expects one"
        def sql = qs[0]
        def answers = ANSWERS[title]
        int found = sql.count("___")
        assert found == answers.size(): "koan '${title}' has ${found} blank(s) but the answer table has ${answers.size()}"
        answers.each { a -> sql = sql.replaceFirst(/___/, java.util.regex.Matcher.quoteReplacement(a)) }
        sql
    }

    private Object koanExpected(String engine, String title) {
        def nested = koanNested(title)
        if (nested != null) {
            return sqlFor(engine).rows(nested).collect { r -> (0..<r.size()).collect { i -> plain(r.getAt(i)) } }
        }
        def raw = koanRawExpected(title)
        raw instanceof List ? (raw as List).collect { row -> (row as List).collect { plain(it) } } : plain(raw)
    }

    private static Object koanRawExpected(String title) {
        def body = koanBody(title)
        def rows = (body =~ /(?s)shouldReturn\(\s*(\[.*?\])\s*,\s*'''/)
        if (rows.find()) return Eval.me(rows.group(1))
        def single = (body =~ /shouldReturn\s+(-?\d+(?:\.\d+)?)\s*,\s*'''/)
        assert single.find(): "koan '${title}' declares neither rows nor a single value with shouldReturn"
        new BigDecimal(single.group(1))
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
