package datazeus.learnsql.series2._05

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 2 · lesson _05
 * "CASE — SQL's If/Then for Labels & Buckets".
 *
 * Every figure the video, the article, the short and the koans put in front of a learner is
 * asserted here, on BOTH engines. The lesson's scripts, and the sections that run them:
 *
 *    freight-band-per-order, orders-per-band                        §1 the shape
 *    orders-per-band-whens-swapped                                  §2 the first true WHEN wins
 *    heavy-label-no-else, not-heavy-no-else, not-heavy-with-else    §3 no ELSE means NULL
 *    shipped-or-waiting                                             §4 the hands-on
 *    orders-and-unshipped-per-country,
 *    unshipped-counted-with-count, waiting-per-employee             §5 CASE inside an aggregate
 *    shipper-names-by-case, shipper-names-by-join,
 *    region-coalesce-and-case                                       §6 the short form, and COALESCE
 *
 * §7 asserts every number the KOANS' comments state, and §8 runs the koans file itself, as
 * written, on both engines (ported from Series 1 · 50 §8).
 *
 * ── THE EARN, AS ARITHMETIC ────────────────────────────────────────────────────────────────
 * Freight bands: 16 light + 34 medium + 29 heavy = 79. Swap the first two WHENs and light is
 * gone: 50 medium, the same 29 heavy. One WHEN and no ELSE: 37 heavy and 42 NULL; filtering
 * "not heavy" over that returns 0, and with an ELSE it returns the 42.
 *
 * ── ORDER, TEXT AND DECIMALS ───────────────────────────────────────────────────────────────
 * Every result the lesson SHOWS is ordered on something that pins it — a unique key, a band's
 * min("Freight"), or a count with a name tie-break over plain ASCII names — so pinning its rows
 * is honest. Money is compared BY VALUE via dec(). What each tool PRINTS is the video's concern
 * (decimalAsShownInCloudBeaver, NULL_AS_SHOWN_IN_CLOUDBEAVER), not this gate's.
 */
class CaseExpressionsSpec extends NorthwindGateSpec {

    // --- 0. The dataset the whole lesson quotes -----------------------------------------------

    def "the dataset is the small Northwind the lesson quotes"() {
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Order Details"').n == 193 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Suppliers"').n == 6 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "ShippedDate" IS NULL').n == 27
        }
    }

    // --- 1. THE SHAPE -----------------------------------------------------------------------------

    @Unroll
    def "[#engine] the band per order: order 1 on 25.1 is medium, order 5 on 11.61 is light"() {
        given:
        def rows = sqlFor(engine).rows(script("freight-band-per-order"))

        expect: "band-result's card, row for row"
        rows.collect { [it.OrderID, dec(it.Freight), it.Band] } ==
                [[1, dec("25.1"), "medium"], [2, dec("15.5"), "light"], [3, dec("40"), "medium"],
                 [4, dec("32.38"), "medium"], [5, dec("11.61"), "light"], [6, dec("45.5"), "medium"]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] 16 light, 34 medium, 29 heavy — every one of the 79 orders gets a band"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-band"))

        expect: "band-counts' card, in min(Freight) order"
        rows.collect { [it.Band, it.Orders] } == [["light", 16], ["medium", 34], ["heavy", 29]]
        rows*.Orders.sum() == 79

        and: "no order sits exactly on a boundary, so 'under 25' and 'under 60' read unambiguously"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "Freight" IN (25, 60)').n == 0

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the trailer's tokens are orders 1-16's real freights, and route as the machine draws"() {
        // intro.tsx TOKENS: freights of orders 1–16 (it shows twelve of them, shuffled).
        given:
        def rows = sqlFor(engine).rows('SELECT "OrderID", "Freight" FROM "Orders" WHERE "OrderID" <= 16 ORDER BY "OrderID"')

        expect:
        rows.collect { dec(it.Freight) } ==
                ["25.1", "15.5", "40", "32.38", "11.61", "45.5", "22.75", "10",
                 "17.03", "24.06", "31.09", "38.12", "45.15", "52.18", "59.21", "66.24"].collect { dec(it) }

        where:
        engine << ENGINES
    }

    // --- 2. THE FIRST TRUE WHEN WINS -----------------------------------------------------------------

    @Unroll
    def "[#engine] swap the first two WHENs and light disappears: 50 medium, 29 heavy"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-band-whens-swapped"))

        expect: "swap-the-whens' card"
        rows.collect { [it.Band, it.Orders] } == [["medium", 50], ["heavy", 29]]

        and: "the 50 is exactly the old light plus the old medium — nothing was lost, it was relabelled"
        rows[0].Orders == 16 + 34

        where:
        engine << ENGINES
    }

    // --- 3. NO ELSE MEANS NULL -----------------------------------------------------------------------

    @Unroll
    def "[#engine] one WHEN and no ELSE: 37 heavy and 42 NULL"() {
        given:
        def rows = sqlFor(engine).rows(script("heavy-label-no-else"))

        expect: "forty-two-nulls' card, NULL last"
        rows.collect { [it.Label, it.Orders] } == [["heavy", 37], [null, 42]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] 'not heavy' is 0 without the ELSE and 42 with it"() {
        expect: "not-heavy-zero and always-else"
        sqlFor(engine).firstRow(script("not-heavy-no-else"))["Not heavy"] == 0
        sqlFor(engine).firstRow(script("not-heavy-with-else"))["Not heavy"] == 42

        where:
        engine << ENGINES
    }

    // --- 4. THE HANDS-ON ------------------------------------------------------------------------------

    @Unroll
    def "[#engine] the hands-on: 52 shipped and 27 waiting"() {
        expect:
        sqlFor(engine).rows(script("shipped-or-waiting")).collect { [it.Status, it.Orders] } ==
                [["shipped", 52], ["waiting", 27]]

        where:
        engine << ENGINES
    }

    // --- 5. CASE INSIDE AN AGGREGATE ---------------------------------------------------------------

    @Unroll
    def "[#engine] per country: Germany 32 orders, 10 unshipped — the SUM and the count versions agree"() {
        given:
        def viaSum = sqlFor(engine).rows(script("orders-and-unshipped-per-country"))
        def viaCount = sqlFor(engine).rows(script("unshipped-counted-with-count"))
        def expected = [["Germany", 32, 10], ["Mexico", 8, 4], ["Sweden", 8, 3], ["UK", 7, 2],
                        ["France", 6, 2], ["Venezuela", 6, 2], ["Argentina", 3, 1], ["Austria", 3, 1],
                        ["Italy", 3, 1], ["USA", 3, 1]]

        expect: "country-result's card — every country, not a top ten"
        viaSum.collect { [it.ShipCountry, it.Orders, it.Unshipped as int] } == expected
        viaSum*.Orders.sum() == 79

        and: "count-version: the missing ELSE on purpose gives the same column"
        viaCount.collect { [it.ShipCountry, it.Orders, it.Unshipped as int] } == expected

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] per employee: Andrew 24/0, Janet 27/2, Nancy 28/25"() {
        expect: "nancy's card — and these three are every employee who took an order"
        sqlFor(engine).rows(script("waiting-per-employee")).collect { [it.FirstName, it.Orders, it.Waiting as int] } ==
                [["Andrew", 24, 0], ["Janet", 27, 2], ["Nancy", 28, 25]]
        sqlFor(engine).firstRow('SELECT count(DISTINCT "EmployeeID") AS n FROM "Orders"').n == 3

        where:
        engine << ENGINES
    }

    // --- 6. THE SHORT FORM, AND COALESCE -----------------------------------------------------------

    @Unroll
    def "[#engine] the simple CASE and the join to Shippers return the same 26, 26, 27"() {
        given:
        def expected = [["Federal Shipping", 26], ["Speedy Express", 26], ["United Package", 27]]

        expect:
        sqlFor(engine).rows(script("shipper-names-by-case")).collect { [it.Shipper, it.Orders] } == expected
        sqlFor(engine).rows(script("shipper-names-by-join")).collect { [it.Shipper, it.Orders] } == expected

        and: "the CASE's hard-coded names are the names stored in the table, id for id"
        sqlFor(engine).rows('SELECT "ShipperID", "CompanyName" FROM "Shippers" ORDER BY "ShipperID"')
                .collect { [it.ShipperID, it.CompanyName] } ==
                [[1, "Speedy Express"], [2, "United Package"], [3, "Federal Shipping"]]

        and: "every order has a ShipVia the CASE covers, so no NULL shipper row"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "ShipVia" NOT IN (1, 2, 3) OR "ShipVia" IS NULL').n == 0

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] COALESCE and the CASE it abbreviates give identical columns, on every customer"() {
        given:
        def shown = sqlFor(engine).rows(script("region-coalesce-and-case"))
        def every = sqlFor(engine).rows(script("region-coalesce-and-case").replace("LIMIT 4;", ""))

        expect: "coalesce-is-case's four rows"
        shown.size() == 4
        shown.every { it["With COALESCE"] == it["With CASE"] }
        every.size() == 25
        every.every { it["With COALESCE"] == it["With CASE"] }

        where:
        engine << ENGINES
    }

    // --- 7. What the KOANS stand on --------------------------------------------------------------

    @Unroll
    def "[#engine] the koan comments' facts are true"() {
        expect: "the schema note: two products have no stock"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "UnitsInStock" = 0').n == 2

        and: "koan 2: with the wide test first, the answer is 13 ok and 7 reorder"
        sqlFor(engine).rows('''SELECT CASE
                                        WHEN "UnitsInStock" <= "ReorderLevel" THEN 'reorder'
                                        WHEN "UnitsInStock" = 0 THEN 'out of stock'
                                        ELSE 'ok'
                                      END AS "Stock", count(*) AS "Products"
                               FROM "Products" GROUP BY "Stock" ORDER BY "Stock"''')
                .collect { [it.Stock, it.Products] } == [["ok", 13], ["reorder", 7]]

        and: "koan 4: without an ELSE the not-discounted count would be 0"
        sqlFor(engine).firstRow('''SELECT count(*) AS n
                                   FROM (SELECT CASE WHEN "Discount" > 0 THEN 'discounted' END AS "Label"
                                         FROM "Order Details") AS t
                                   WHERE "Label" <> 'discounted' ''').n == 0

        and: "koan 6: 2022 is December only"
        def dec2022 = sqlFor(engine).firstRow('''SELECT count(DISTINCT EXTRACT(MONTH FROM "OrderDate")) AS n,
                                                     min(EXTRACT(MONTH FROM "OrderDate")) AS m
                                              FROM "Orders" WHERE EXTRACT(YEAR FROM "OrderDate") = 2022''')
        dec(dec2022.n) == dec(1)
        dec(dec2022.m) == dec(12)

        and: "koan 6: the discount is only ever 0, 0.05 or 0.1, so '= 0' and '> 0' split every line"
        sqlFor(engine).rows('SELECT DISTINCT "Discount" AS d FROM "Order Details" ORDER BY d')
                .collect { dec(it.d) } == [dec("0"), dec("0.05"), dec("0.1")]

        and: "koan 7: three products tie on 13 lines, and the fourth has fewer — LIMIT 3 cuts no tie"
        def lineCounts = sqlFor(engine).rows('''SELECT count(*) AS n FROM "Order Details"
                                                GROUP BY "ProductID" ORDER BY n DESC LIMIT 4''')*.n
        lineCounts.take(3) == [13, 13, 13]
        lineCounts[3] < 13

        and: "koan 8: three suppliers have no region"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Suppliers" WHERE "Region" IS NULL').n == 3

        and: "koans 1 and 9: ONE product sits exactly on an edge — Uncle Bobs at 30 — and both koans' wording puts it in premium"
        // Koan 1: "under 30 is standard, every other product is premium"; koan 9: "premium (30 and
        // above)". A learner who writes <= 30 for standard gets 10 standard and 5 premium: wrong, and
        // the comment's own words say why.
        sqlFor(engine).rows('SELECT "ProductName" AS p FROM "Products" WHERE "UnitPrice" IN (15, 30)')*.p ==
                ["Uncle Bobs Organic Dried Pears"]
        dec(sqlFor(engine).firstRow('SELECT "UnitPrice" AS u FROM "Products" WHERE "ProductName" = \'Uncle Bobs Organic Dried Pears\'').u) == dec(30)

        and: "koan 10: every order has an OrderDate, so the three years hold all 79"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "OrderDate" IS NULL').n == 0

        where:
        engine << ENGINES
    }

    // --- 8. THE KOAN FILE ITSELF, RUN AS WRITTEN (Series 1 · 50 §8) --------------------------------

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
        titles[0] == "a label for every product: finish the CASE"
        titles[1] == "the first true WHEN wins: write the tests in the right order"
        titles[2] == "no ELSE means NULL: count only what the CASE labelled"
        koanQueries(titles[8])*.trim() == ["___"]
        koanQueries(titles[9])*.trim() == ["___"]
    }

    def "the video's editor mock quotes koan 1 verbatim"() {
        // ED_CODE and animFill in the video file hard-code koan 1's blank line; if the koan moves,
        // the mock would draw a line the file no longer has.
        expect:
        koanBody("a label for every product: finish the CASE").contains("___ 'premium'")
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static String script(String name) {
        new File("../courses/learnsql/series2-intermediate/05-case-expressions/scripts/${name}.sql").text
    }

    private static String koansSource() {
        new File("src/koans/groovy/datazeus/learnsql/series2/_05/CaseExpressionsKoans.groovy").text
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
            "a label for every product: finish the CASE"                     : ["ELSE"],
            "the first true WHEN wins: write the tests in the right order"   : ["WHEN \"UnitsInStock\" = 0 THEN 'out of stock' WHEN \"UnitsInStock\" <= \"ReorderLevel\" THEN 'reorder'"],
            "no ELSE means NULL: count only what the CASE labelled"          : ['"Label"'],
            "a NULL fails not-equal too: give the rest a label"              : ["ELSE 'full price'"],
            "CASE inside SUM: a column for one status"                       : ["THEN 1 ELSE 0"],
            "CASE inside SUM: money with and without a discount"             : ['d."Discount" > 0'],
            "count(CASE ... END): the missing ELSE, on purpose"              : ['CASE WHEN d."Discount" > 0 THEN 1 END'],
            "COALESCE is a CASE: write it the long way"                      : ['ELSE "Region"'],
            "write the whole query: price bands as columns, per category"    : ['''
                SELECT c."CategoryName",
                       SUM(CASE WHEN p."UnitPrice" < 15 THEN 1 ELSE 0 END) AS "Budget",
                       SUM(CASE WHEN p."UnitPrice" >= 15 AND p."UnitPrice" < 30 THEN 1 ELSE 0 END) AS "Standard",
                       SUM(CASE WHEN p."UnitPrice" >= 30 THEN 1 ELSE 0 END) AS "Premium"
                FROM "Products" p
                JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
                GROUP BY c."CategoryName"
                ORDER BY c."CategoryName"
            '''],
            "write the whole query: shipped and waiting, per year"           : ['''
                SELECT EXTRACT(YEAR FROM "OrderDate") AS "Year",
                       count(*) AS "Orders",
                       SUM(CASE WHEN "ShippedDate" IS NOT NULL THEN 1 ELSE 0 END) AS "Shipped",
                       SUM(CASE WHEN "ShippedDate" IS NULL THEN 1 ELSE 0 END) AS "Waiting"
                FROM "Orders"
                GROUP BY EXTRACT(YEAR FROM "OrderDate")
                ORDER BY "Year"
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
