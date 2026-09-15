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
 *    shipper-names-by-case, shipper-names-by-join,
 *    region-coalesce-and-case                                       §4 the short form, and COALESCE
 *    shipped-or-no-ship-date                                        §5 the hands-on (the case opens)
 *    no-ship-date-per-country, no-ship-date-counted-with-count      §6 CASE inside an aggregate
 *    share-per-country-integer-division                             §7 the share: PER ENGINE
 *    share-per-country, share-company                               §7 the share, portable
 *    no-ship-date-per-employee                                      §8 whose orders
 *
 * §9 asserts every number the KOANS' comments state, and §10 runs the koans file itself, as
 * written, on both engines (ported from Series 1 · 50 §8).
 *
 * ── THE EARN, AS ARITHMETIC ────────────────────────────────────────────────────────────────
 * Freight bands: 16 light + 34 medium + 29 heavy = 79. Swap the first two WHENs and light is
 * gone: 50 medium, the same 29 heavy. One WHEN and no ELSE: 37 heavy and 42 NULL; filtering
 * "not heavy" over that returns 0, and with an ELSE it returns the 42.
 *
 * ── THE ONE ASSERTION THAT IS DELIBERATELY NOT "THE SAME ON BOTH ENGINES" (§7) ──────────────
 * SUM(CASE … THEN 1 ELSE 0 END) / count(*) is a whole number divided by a whole number. On
 * PostgreSQL that is INTEGER division — bigint / bigint is a bigint — so every country's share is
 * 0, which is what CloudBeaver shows and what the video and the article print. On DuckDB `/` is
 * always floating-point division, so Germany's share is 0.3125 (a DOUBLE). Both are asserted, each
 * on its own engine, WITH THE TYPE, exactly as Series 1 · 15 asserts NULL placement per engine;
 * then the 100.0 * … version is asserted identical on both, which is what turns "write it with
 * 100.0 and it stops depending on the engine" into a claim the gate proves. The two near-misses
 * the article names (100 without the .0, and dividing before multiplying) are asserted the same way.
 * THE ENGINE DIFFERENCE HAS NO KOAN: a DuckDB koan would go green for the column CloudBeaver
 * shows as zeros (Series 1 · 20's precedent). The share koans use the portable form.
 *
 * ── THE CASE, AND WHAT THIS GATE DOES NOT CLAIM ────────────────────────────────────────────
 * 27 of 79 orders have no "ShippedDate"; per country 31.3 for Germany against 34.2 for the
 * company; per person 25 of Nancy's 28. The lesson says "her orders" and "a number is not a
 * verdict"; this spec asserts the counts, never a reason for them.
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

    // --- 4. THE SHORT FORM, AND COALESCE -----------------------------------------------------------

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

    // --- 5. THE HANDS-ON: THE CASE OPENS ---------------------------------------------------------------

    @Unroll
    def "[#engine] the hands-on: 52 shipped and 27 with no ship date"() {
        expect: "hands-on-label's answer line, most orders first"
        sqlFor(engine).rows(script("shipped-or-no-ship-date")).collect { [it.Status, it.Orders] } ==
                [["shipped", 52], ["no ship date", 27]]

        where:
        engine << ENGINES
    }

    // --- 6. CASE INSIDE AN AGGREGATE ---------------------------------------------------------------

    private static final List COUNTRIES = [["Germany", 32, 10], ["Mexico", 8, 4], ["Sweden", 8, 3], ["UK", 7, 2],
                                           ["France", 6, 2], ["Venezuela", 6, 2], ["Argentina", 3, 1], ["Austria", 3, 1],
                                           ["Italy", 3, 1], ["USA", 3, 1]]

    @Unroll
    def "[#engine] per country: Germany 32 orders, 10 with no ship date — the SUM and the count versions agree"() {
        given:
        def viaSum = sqlFor(engine).rows(script("no-ship-date-per-country"))
        def viaCount = sqlFor(engine).rows(script("no-ship-date-counted-with-count"))

        expect: "country-result's card — every country, not a top ten"
        viaSum.collect { [it.ShipCountry, it.Orders, it["No ship date"] as int] } == COUNTRIES
        viaSum*.Orders.sum() == 79
        viaSum.collect { it["No ship date"] as int }.sum() == 27

        and: "count-version: the missing ELSE on purpose gives the same column"
        viaCount.collect { [it.ShipCountry, it.Orders, it["No ship date"] as int] } == COUNTRIES

        where:
        engine << ENGINES
    }

    // --- 7. THE SHARE — PER ENGINE, THEN PORTABLE ------------------------------------------------------

    def "postgres: SUM(CASE …) / count(*) is integer division — every country's share is a whole-number 0"() {
        // THE SLIDE'S WHOLE CLAIM (share-zero) and the article's CloudBeaver table. bigint / bigint is
        // a bigint on PostgreSQL, so the fraction is thrown away. If this ever comes back as a decimal,
        // the video, the article and the flashcard that say "CloudBeaver shows 0" are all wrong.
        given:
        def rows = sqlFor("postgres").rows(script("share-per-country-integer-division"))

        expect: "the same ten countries and counts as the report before it"
        rows.collect { [it.ShipCountry, it.Orders, it["No ship date"] as int] } == COUNTRIES

        and: "every share is 0, and it is a WHOLE-NUMBER type — not a decimal that happens to round to 0"
        rows.every { it.Share == 0 }
        rows.every { r -> [Integer, Long, BigInteger].any { t -> t.isInstance(r.Share) } }
    }

    def "duckdb: the same query keeps the fraction — Germany 0.3125, as a DOUBLE"() {
        // The Callout's "run it in DuckDB and Germany's share is 0.3125". DuckDB's `/` is always
        // floating-point division (`//` is its integer division), so the koans' engine would pass a
        // query CloudBeaver shows as zeros — which is why this rule has no koan.
        given:
        def rows = sqlFor("duckdb").rows(script("share-per-country-integer-division"))

        expect:
        rows.collect { [it.ShipCountry, it.Orders, it["No ship date"] as int] } == COUNTRIES
        rows.every { it.Share instanceof Double }
        rows[0].ShipCountry == "Germany"
        rows[0].Share == 0.3125d
        rows[1].Share == 0.5d

        and: "every share is exactly its count divided by its orders"
        rows.every { r -> Math.abs((r.Share as double) - (r["No ship date"] as double) / (r.Orders as double)) < 1e-12 }
    }

    @Unroll
    def "[#engine] 100.0 * … / count(*), rounded to 1: the same shares on both engines"() {
        given:
        def rows = sqlFor(engine).rows(script("share-per-country"))

        expect: "share-result's card and the article's table"
        rows.collect { [it.ShipCountry, it.Orders, it["No ship date"] as int, dec(it["Share %"])] } ==
                [["Germany", 32, 10, dec("31.3")], ["Mexico", 8, 4, dec("50")], ["Sweden", 8, 3, dec("37.5")],
                 ["UK", 7, 2, dec("28.6")], ["France", 6, 2, dec("33.3")], ["Venezuela", 6, 2, dec("33.3")],
                 ["Argentina", 3, 1, dec("33.3")], ["Austria", 3, 1, dec("33.3")], ["Italy", 3, 1, dec("33.3")],
                 ["USA", 3, 1, dec("33.3")]]

        and: "Mexico's 50 is 4 of 8: one order either way moves it to 62.5 or 37.5"
        dec(100.0 * 5 / 8) == dec("62.5")
        dec(100.0 * 3 / 8) == dec("37.5")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the company: 79 orders, 27 with no ship date, 34.2 — and Germany's 31.3 is below it"() {
        given:
        def company = sqlFor(engine).firstRow(script("share-company"))
        def germany = sqlFor(engine).rows(script("share-per-country")).find { it.ShipCountry == "Germany" }
        def countries = sqlFor(engine).rows(script("no-ship-date-per-country"))

        expect: "company-share's one row"
        company.Orders == 79
        (company["No ship date"] as int) == 27
        dec(company["Share %"]) == dec("34.2")

        and: "Germany has the MOST orders with no ship date, and a share below the company's"
        (germany["No ship date"] as int) == countries.collect { it["No ship date"] as int }.max()
        countries.count { (it["No ship date"] as int) == 10 } == 1
        dec(germany["Share %"]) < dec(company["Share %"])

        where:
        engine << ENGINES
    }

    def "the article's two near-misses: 100 without the .0, and dividing before multiplying — per engine"() {
        // "Writing 100 * SUM(…) / count(*) without the .0 gives PostgreSQL 31 for Germany instead of
        // 31.3, and SUM(…) / count(*) * 100.0 divides first and gives 0 again." Neither is a script
        // (neither is shown as a query), so they are asserted inline, on Germany, on both engines.
        given:
        def noPoint = '''SELECT ROUND(100 * SUM(CASE WHEN "ShippedDate" IS NULL THEN 1 ELSE 0 END) / count(*), 1) AS s
                         FROM "Orders" WHERE "ShipCountry" = 'Germany' '''
        def divideFirst = '''SELECT ROUND(SUM(CASE WHEN "ShippedDate" IS NULL THEN 1 ELSE 0 END) / count(*) * 100.0, 1) AS s
                             FROM "Orders" WHERE "ShipCountry" = 'Germany' '''

        expect: "PostgreSQL: 31 and 0"
        dec(sqlFor("postgres").firstRow(noPoint).s) == dec("31")
        dec(sqlFor("postgres").firstRow(divideFirst).s) == dec("0")

        and: "DuckDB forgives both, which is the whole danger of trying it there first"
        dec(sqlFor("duckdb").firstRow(noPoint).s) == dec("31.3")
        dec(sqlFor("duckdb").firstRow(divideFirst).s) == dec("31.3")
    }

    // --- 8. WHOSE ORDERS ----------------------------------------------------------------------------

    @Unroll
    def "[#engine] per employee: Andrew 24/0, Janet 27/2, Nancy 28/25"() {
        expect: "per-rep-result's card — and these three are every employee who took an order"
        sqlFor(engine).rows(script("no-ship-date-per-employee")).collect { [it.FirstName, it.Orders, it["No ship date"] as int] } ==
                [["Andrew", 24, 0], ["Janet", 27, 2], ["Nancy", 28, 25]]
        sqlFor(engine).firstRow('SELECT count(DISTINCT "EmployeeID") AS n FROM "Orders"').n == 3

        where:
        engine << ENGINES
    }

    // --- 9. What the KOANS stand on --------------------------------------------------------------

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

        and: "koan 5: three suppliers have no region"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Suppliers" WHERE "Region" IS NULL').n == 3

        and: "koan 7: 2022 is December only"
        def dec2022 = sqlFor(engine).firstRow('''SELECT count(DISTINCT EXTRACT(MONTH FROM "OrderDate")) AS n,
                                                     min(EXTRACT(MONTH FROM "OrderDate")) AS m
                                              FROM "Orders" WHERE EXTRACT(YEAR FROM "OrderDate") = 2022''')
        dec(dec2022.n) == dec(1)
        dec(dec2022.m) == dec(12)

        and: "koans 7 and 9: the discount is only ever 0, 0.05 or 0.1, so '= 0' and '> 0' split every line"
        sqlFor(engine).rows('SELECT DISTINCT "Discount" AS d FROM "Order Details" ORDER BY d')
                .collect { dec(it.d) } == [dec("0"), dec("0.05"), dec("0.1")]

        and: "koan 8: three products tie on 13 lines, and the fourth has fewer — LIMIT 3 cuts no tie"
        def lineCounts = sqlFor(engine).rows('''SELECT count(*) AS n FROM "Order Details"
                                                GROUP BY "ProductID" ORDER BY n DESC LIMIT 4''')*.n
        lineCounts.take(3) == [13, 13, 13]
        lineCounts[3] < 13

        and: "koan 9: Beverages is 15 of 30, and the eight categories hold all 193 lines"
        def cat = sqlFor(engine).rows('''SELECT c."CategoryName" AS name, count(*) AS n,
                                              SUM(CASE WHEN d."Discount" > 0 THEN 1 ELSE 0 END) AS k
                                       FROM "Order Details" d
                                       JOIN "Products" p ON p."ProductID" = d."ProductID"
                                       JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
                                       GROUP BY c."CategoryName" ORDER BY c."CategoryName"''')
        cat.find { it.name == "Beverages" }.with { [it.n as int, it.k as int] } == [30, 15]
        cat.size() == 8
        cat.collect { it.n as int }.sum() == 193

        and: "koan 11: seven products are due to be reordered in all, across six suppliers, and no stock or reorder level is NULL"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "UnitsInStock" <= "ReorderLevel"').n == 7
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "UnitsInStock" IS NULL OR "ReorderLevel" IS NULL').n == 0
        sqlFor(engine).firstRow('SELECT count(DISTINCT "SupplierID") AS n FROM "Products"').n == 6

        and: "koans 1 and 10: ONE product sits exactly on an edge — Uncle Bobs at 30 — and both koans' wording puts it in premium"
        // Koan 1: "under 30 is standard, every other product is premium"; koan 10: "premium (30 and
        // above)". A learner who writes <= 30 for standard gets 10 standard and 5 premium: wrong, and
        // the comment's own words say why.
        sqlFor(engine).rows('SELECT "ProductName" AS p FROM "Products" WHERE "UnitPrice" IN (15, 30)')*.p ==
                ["Uncle Bobs Organic Dried Pears"]
        dec(sqlFor(engine).firstRow('SELECT "UnitPrice" AS u FROM "Products" WHERE "ProductName" = \'Uncle Bobs Organic Dried Pears\'').u) == dec(30)

        where:
        engine << ENGINES
    }

    def "koan 9's warning is true: on PostgreSQL the category share WITHOUT 100.0 is 0 for every category"() {
        // The koan comment tells the learner that DuckDB would forgive a share written without the
        // decimal point and CloudBeaver would not. This is the CloudBeaver half, on the koan's own data.
        expect:
        sqlFor("postgres").rows('''SELECT SUM(CASE WHEN d."Discount" > 0 THEN 1 ELSE 0 END) / count(*) AS s
                                    FROM "Order Details" d
                                    JOIN "Products" p ON p."ProductID" = d."ProductID"
                                    GROUP BY p."CategoryID"''').every { it.s == 0 }
        sqlFor("duckdb").rows('''SELECT SUM(CASE WHEN d."Discount" > 0 THEN 1 ELSE 0 END) / count(*) AS s
                                  FROM "Order Details" d
                                  JOIN "Products" p ON p."ProductID" = d."ProductID"
                                  GROUP BY p."CategoryID"''').every { it.s > 0 && it.s < 1 }
    }

    // --- 10. THE KOAN FILE ITSELF, RUN AS WRITTEN (Series 1 · 50 §8) -------------------------------

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

    def "the koans file matches the video's koans slide: eleven koans, its three names, two whole queries last"() {
        given:
        def titles = (koansSource() =~ /(?m)^    def "(.+?)"\(\)/).collect { it[1] }

        expect:
        titles == (ANSWERS.keySet() as List)
        titles.size() == 11
        titles[0] == "a label for every product: finish the CASE"
        titles[1] == "the first true WHEN wins: write the tests in the right order"
        titles[2] == "no ELSE means NULL: count only what the CASE labelled"
        koanQueries(titles[9])*.trim() == ["___"]
        koanQueries(titles[10])*.trim() == ["___"]

        and: "the share koans are written the portable way — 100.0, with the point"
        ANSWERS["a share of the lines: 100.0 before you divide"][0].startsWith("100.0 * ")
        ANSWERS["write the whole query: what each supplier needs to reorder, and its share"][0].contains("100.0 * SUM(")
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
            "COALESCE is a CASE: write it the long way"                      : ['ELSE "Region"'],
            "CASE inside SUM: a column for one status"                       : ["THEN 1 ELSE 0"],
            "CASE inside SUM: money with and without a discount"             : ['d."Discount" > 0'],
            "count(CASE ... END): the missing ELSE, on purpose"              : ['CASE WHEN d."Discount" > 0 THEN 1 END'],
            "a share of the lines: 100.0 before you divide"                  : ['100.0 * SUM(CASE WHEN d."Discount" > 0 THEN 1 ELSE 0 END) / count(*)'],
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
            "write the whole query: what each supplier needs to reorder, and its share": ['''
                SELECT s."CompanyName",
                       count(*) AS "Products",
                       SUM(CASE WHEN p."UnitsInStock" <= p."ReorderLevel" THEN 1 ELSE 0 END) AS "To reorder",
                       ROUND(100.0 * SUM(CASE WHEN p."UnitsInStock" <= p."ReorderLevel" THEN 1 ELSE 0 END)
                             / count(*), 1) AS "To reorder %"
                FROM "Suppliers" s
                JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                GROUP BY s."CompanyName"
                ORDER BY s."CompanyName"
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
