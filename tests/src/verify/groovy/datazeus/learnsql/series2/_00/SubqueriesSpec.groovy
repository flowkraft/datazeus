package datazeus.learnsql.series2._00

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

import java.sql.SQLException

/**
 * VERIFIED spec = the PUBLISH GATE for Series 2 · lesson _00
 * "Subqueries — Using One Query's Result Inside Another".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted here,
 * on BOTH engines, plus the two error messages the video draws. The lesson's scripts, and the
 * sections that run them:
 *
 *    avg-price, above-average-price-typed, above-average-price,
 *    at-or-below-average-price                                     §1 a value
 *    aggregate-in-where, dearer-than-beverages-error                §2 the two refusals
 *    dearer-than-every-beverage                                     §2
 *    chai-customers, chai-customers-joined                          §3 a list
 *    average-sale, customers-above-the-average-line,
 *    average-customer, customers-above-the-average-customer         §4 a table, and the trap
 *    sales-with-no-ship-date,
 *    customers-above-the-average-customer-delivered                 §4b the case file
 *
 * §5 asserts every number the KOANS' comments state, and §6 runs the koans file itself, as
 * written, on both engines (ported from Series 1 · 50 §8).
 *
 * ── THE EARN, AS ARITHMETIC ────────────────────────────────────────────────────────────────
 * The average ORDER LINE is 301.31 and every one of the 25 customers beats it; the average
 * CUSTOMER is 2326.13 and 9 beat it. Both averages are asserted, the 25 and the 9 are asserted,
 * and so is the fact that makes the first one wrong for this question: every customer's total is
 * a sum of at least five order lines, never a single line.
 *
 * ── ORDER, TEXT AND DECIMALS ───────────────────────────────────────────────────────────────
 * Every result the lesson SHOWS carries an ORDER BY on a unique key (asserted where it matters),
 * so pinning its rows is honest. NAME SORTS ARE WHERE THE ENGINES DIFFER: DuckDB orders by bytes
 * ("LILA-Supermercado" before "Lehmanns Marktstand"), PostgreSQL by collation. No list pinned
 * here by name order contains such a pair — asserted on the chai list by running it on both.
 * Money is compared BY VALUE via dec(): DuckDB hands back DECIMAL or DOUBLE, PostgreSQL numeric,
 * and 28.72 must equal 28.7200. What each tool PRINTS is the video's concern
 * (decimalAsShownInCloudBeaver), not this gate's.
 */
class SubqueriesSpec extends NorthwindGateSpec {

    // --- 0. The dataset the whole lesson quotes -----------------------------------------------

    def "the dataset is the small Northwind the lesson quotes"() {
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers"').n == 25 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Order Details"').n == 193 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Suppliers"').n == 6
        }
    }

    // --- 1. A VALUE: products against the average price ----------------------------------------

    @Unroll
    def "[#engine] the average price is 28.72, and six products sit above it"() {
        given:
        def typed = sqlFor(engine).rows(script("above-average-price-typed"))
        def bracketed = sqlFor(engine).rows(script("above-average-price"))

        expect: "warm-up-average's single figure"
        dec(sqlFor(engine).firstRow(script("avg-price")).values().first()) == dec("28.72")

        and: "six-products' card, row for row, in the order the query promises"
        typed.collect { [it.ProductName, dec(it.UnitPrice)] } ==
                [["Thuringer Rostbratwurst", dec("123.79")], ["Mishi Kobe Niku", dec("97")],
                 ["Gnocchi di nonna Alice", dec("38")], ["Camembert Pierrot", dec("34")],
                 ["Ikura", dec("31")], ["Uncle Bobs Organic Dried Pears", dec("30")]]

        and: "BRACKETS' CLAIM: the subquery returns exactly what the typed number did, today"
        bracketed.collect { [it.ProductName, dec(it.UnitPrice)] } == typed.collect { [it.ProductName, dec(it.UnitPrice)] }

        and: "the six prices are all different, so the ORDER BY pins the row order"
        typed*.UnitPrice.collect { dec(it) }.toSet().size() == 6

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the hands-on: 14 products at or below the average, because none sits on it"() {
        // "Six are above it. Predict the rest." The rest is 14 ONLY because no product costs
        // exactly the average — asserted, or the prediction the slide invites would be a trick.
        expect:
        sqlFor(engine).firstRow(script("at-or-below-average-price")).values().first() == 14
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Products"
                                   WHERE "UnitPrice" = (SELECT AVG("UnitPrice") FROM "Products")''').n == 0

        where:
        engine << ENGINES
    }

    // --- 2. THE TWO REFUSALS --------------------------------------------------------------------

    def "PostgreSQL refuses an average in WHERE, with the exact error the video draws"() {
        // the-obvious-query's ErrorPanel: SQLSTATE 42803, "aggregate functions are not allowed in
        // WHERE", Position 71 — the offset of AVG in the script as written.
        when:
        sqlFor("postgres").rows(script("aggregate-in-where"))

        then:
        SQLException e = thrown()
        e.SQLState == "42803"
        e.message.contains("aggregate functions are not allowed in WHERE")
        e.message.contains("Position: 71")
    }

    def "DuckDB refuses it too, in its own words"() {
        // The koans run on DuckDB: if it accepted this, a learner could keep an aggregate in a
        // WHERE there and be refused by CloudBeaver. It does not.
        when:
        sqlFor("duckdb").rows(script("aggregate-in-where"))

        then:
        SQLException e = thrown()
        e.message.contains("WHERE clause cannot contain aggregates")
    }

    def "PostgreSQL refuses three prices where one value belongs, with the exact error the video draws"() {
        when:
        sqlFor("postgres").rows(script("dearer-than-beverages-error"))

        then:
        SQLException e = thrown()
        e.SQLState == "21000"
        e.message.contains("more than one row returned by a subquery used as an expression")
    }

    def "DuckDB 1.1.3 refuses it too — it does not silently take the first row"() {
        // Older DuckDB versions DID take the first row without a word. The koans pin 1.1.3 in the
        // pom; koan 3 depends on this refusal, so it is asserted rather than assumed.
        when:
        sqlFor("duckdb").rows(script("dearer-than-beverages-error"))

        then:
        SQLException e = thrown()
        e.message.contains("More than one row returned by a subquery used as an expression")
    }

    @Unroll
    def "[#engine] there are three beverages, and MAX turns their prices into one value: 10 products dearer"() {
        given:
        def rows = sqlFor(engine).rows(script("dearer-than-every-beverage"))

        expect: "one-value-only: Leo's 'three... so that's three prices'"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "CategoryID" = 1').n == 3

        and: "the dearest beverage is Chang, at 19 (the article names it)"
        dec(sqlFor(engine).firstRow('SELECT MAX("UnitPrice") AS m FROM "Products" WHERE "CategoryID" = 1').m) == dec("19")
        sqlFor(engine).rows('SELECT "ProductName" AS n FROM "Products" WHERE "CategoryID" = 1 AND "UnitPrice" = 19')*.n == ["Chang"]

        and: "pick-one-value: ten products, Thuringer first, Ravioli Angelo last"
        rows.size() == 10
        rows.first().ProductName == "Thuringer Rostbratwurst"
        rows.last().ProductName == "Ravioli Angelo"
        dec(rows.last().UnitPrice) == dec("19.5")

        where:
        engine << ENGINES
    }

    // --- 3. A LIST: who bought Chai ----------------------------------------------------------------

    @Unroll
    def "[#engine] IN returns 8 customers, the join returns 10 rows, and Alfreds is three of them"() {
        given:
        def viaIn = sqlFor(engine).rows(script("chai-customers"))*.CompanyName
        def viaJoin = sqlFor(engine).rows(script("chai-customers-joined"))*.CompanyName

        expect: "a-list says 'Chai is product 1'"
        sqlFor(engine).firstRow('SELECT "ProductName" AS n FROM "Products" WHERE "ProductID" = 1').n == "Chai"

        and: "eight-not-ten's card, in order — and no L-name pair here, so both engines sort alike"
        viaIn == ["Alfreds Futterkiste", "Around the Horn", "Berglunds snabbköp", "Drachenblut Delikatessen",
                  "Ernst Handel", "Great Lakes Food Market", "Island Trading", "Morgenstern Gesundkost"]

        and: "the-join-version's card: ten rows, Alfreds three times, the same eight names underneath"
        viaJoin.size() == 10
        viaJoin.take(3) == ["Alfreds Futterkiste"] * 3
        viaJoin.toSet() == viaIn.toSet()

        and: "Leo's premise: Chai sold on ten order lines"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Order Details" WHERE "ProductID" = 1').n == 10

        where:
        engine << ENGINES
    }

    // --- 4. A TABLE, AND THE TRAP ------------------------------------------------------------------

    @Unroll
    def "[#engine] the average order line is 301.31 — over Order Details alone and over the report's joins"() {
        // the-real-question computes it over "Order Details"; the report joins three tables. Every
        // line appears exactly once in both, so the two agree — asserted, because the article says so.
        expect:
        dec(sqlFor(engine).firstRow(script("average-sale")).values().first()) == dec("301.31")
        dec(sqlFor(engine).firstRow('''SELECT ROUND(AVG(d."UnitPrice" * d."Quantity" * (1 - d."Discount")), 2) AS a
                                       FROM "Customers" c
                                       JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                                       JOIN "Order Details" d ON d."OrderID" = o."OrderID"''').a) == dec("301.31")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] against the average line, all 25 customers are 'above average'"() {
        given:
        def rows = sqlFor(engine).rows(script("customers-above-the-average-line"))

        expect: "everyone-above: 25 of 25"
        rows.size() == 25

        and: "the card's top three and bottom two"
        rows.take(3).collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Cactus Comidas para llevar", dec("4567.8")], ["Blauer See Delikatessen", dec("4318.8")],
                 ["Frankenversand", dec("4267.27")]]
        rows.takeRight(2).collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Die Wandernde Kuh", dec("991.76")], ["QUICK-Stop", dec("691.28")]]

        and: "the totals are all different, so the row order is the query's promise, not luck"
        rows.collect { dec(it["Total sales"]) }.toSet().size() == 25

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the average customer is 2326.13, nearly eight times the average line, and 9 customers beat it"() {
        given:
        def nine = sqlFor(engine).rows(script("customers-above-the-average-customer"))
        def all = sqlFor(engine).rows(script("customers-above-the-average-line"))

        expect: "a-table's figure"
        dec(sqlFor(engine).firstRow(script("average-customer")).values().first()) == dec("2326.13")

        and: "two-averages: 'nearly eight times apart' — 7.72"
        new BigDecimal("2326.13").divide(new BigDecimal("301.31"), 2, java.math.RoundingMode.HALF_UP) == new BigDecimal("7.72")

        and: "nine's card, row for row"
        nine.collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Cactus Comidas para llevar", dec("4567.8")], ["Blauer See Delikatessen", dec("4318.8")],
                 ["Frankenversand", dec("4267.27")], ["Lehmanns Marktstand", dec("4077.12")],
                 ["Ernst Handel", dec("3902.78")], ["Alfreds Futterkiste", dec("3838.43")],
                 ["Morgenstern Gesundkost", dec("3289.13")], ["Around the Horn", dec("2701.58")],
                 ["Great Lakes Food Market", dec("2683.28")]]

        and: "the first customer BELOW the line is LILA-Supermercado on 2127.61, as Leo says"
        all[9].CompanyName == "LILA-Supermercado"
        dec(all[9]["Total sales"]) == dec("2127.61")

        and: "THE SAME OUTER QUERY, ONE BRACKET CHANGED: the nine are exactly the top nine of the 25"
        nine*.CompanyName == all.take(9)*.CompanyName

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] why 301.31 is the wrong average: every customer's total is many lines, never one"() {
        // average-of-what: "a customer's total is lines added together, so of course it beats one
        // line". True here because every customer has at least five lines (the article says "at
        // least five") — asserted, since a customer with a single small line would be the one
        // exception the sentence denies.
        expect:
        sqlFor(engine).firstRow('''SELECT min(n) AS m FROM (
                                     SELECT o."CustomerID", count(*) AS n
                                     FROM "Orders" o JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                                     GROUP BY o."CustomerID") t''').m == 5

        where:
        engine << ENGINES
    }

    // --- 4b. THE CASE FILE: the report counted orders with no ship date -------------------------
    // The video's case-* slides and the article's "The report counted orders with no ship date" and
    // "The nine, on delivered sales". Figures from .docs/plan-sql-series2-story.md §2, measured on
    // DuckDB 2026-09-15 and asserted here on both engines.

    @Unroll
    def "[#engine] two values side by side: 19169 of 58153.31 is on orders with no ship date — a third"() {
        given:
        def rows = sqlFor(engine).rows(script("sales-with-no-ship-date"))

        expect: "case-no-ship-date's premise: in Northwind, 27 of the 79 orders have no ship date"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "ShippedDate" IS NULL').n == 27
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79

        and: "case-a-third's card: one row, the two values"
        rows.size() == 1
        dec(rows[0]["No ship date"]) == dec("19169")
        dec(rows[0]["All sales"]) == dec("58153.31")

        and: "'a third' — 19169 / 58153.31 = 0.33"
        new BigDecimal("19169").divide(new BigDecimal("58153.31"), 2, java.math.RoundingMode.HALF_UP) == new BigDecimal("0.33")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] on delivered sales, 7 customers beat the average delivered customer, 1559.37 — and Cactus is not one of them"() {
        given:
        def seven = sqlFor(engine).rows(script("customers-above-the-average-customer-delivered"))
        def nine = sqlFor(engine).rows(script("customers-above-the-average-customer"))
        def delivered = sqlFor(engine).rows('''
                SELECT c."CompanyName" AS n,
                       ROUND(SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")), 2) AS t
                FROM "Customers" c
                JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                WHERE o."ShippedDate" IS NOT NULL
                GROUP BY c."CompanyName"
                ORDER BY t DESC''')

        expect: "case-seven's card, row for row"
        seven.collect { [it.CompanyName, dec(it["Delivered sales"])] } ==
                [["Lehmanns Marktstand", dec("3881.75")], ["Ernst Handel", dec("3812.63")],
                 ["Alfreds Futterkiste", dec("3748.28")], ["Frankenversand", dec("3581.22")],
                 ["Morgenstern Gesundkost", dec("2782.96")], ["LILA-Supermercado", dec("1728.38")],
                 ["Blauer See Delikatessen", dec("1626")]]

        and: "the average delivered customer is 1559.37"
        dec(sqlFor(engine).firstRow('''SELECT ROUND(AVG(t."Total"), 2) AS a
                                       FROM (SELECT o."CustomerID",
                                                    SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
                                             FROM "Orders" o
                                             JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                                             WHERE o."ShippedDate" IS NOT NULL
                                             GROUP BY o."CustomerID") AS t''').a) == dec("1559.37")

        and: "run the inside on its own: 25 rows — every customer has an order with a ship date, so it is still the average of all 25"
        delivered.size() == 25

        and: "NOTHING NEW IN THE SQL: the nine's script, plus the two WHERE lines and the renamed column — nothing else"
        lines(script("customers-above-the-average-customer-delivered"))
                .findAll { !it.contains('"ShippedDate" IS NOT NULL') }
                *.replace('"Delivered sales"', '"Total sales"') == lines(script("customers-above-the-average-customer"))
        lines(script("customers-above-the-average-customer-delivered")).count { it.contains('"ShippedDate" IS NOT NULL') } == 2

        and: "three of the nine drop out — Cactus, Around the Horn, Great Lakes — and LILA-Supermercado comes in"
        (nine*.CompanyName - seven*.CompanyName).toSet() ==
                ["Cactus Comidas para llevar", "Around the Horn", "Great Lakes Food Market"] as Set
        (seven*.CompanyName - nine*.CompanyName) == ["LILA-Supermercado"]

        and: "case-cactus: number one on what it ordered, 4567.8; 1181.95 delivered, 14th of 25"
        nine[0].CompanyName == "Cactus Comidas para llevar"
        dec(nine[0]["Total sales"]) == dec("4567.8")
        delivered.findIndexOf { it.n == "Cactus Comidas para llevar" } == 13
        dec(delivered[13].t) == dec("1181.95")

        and: "the delivered totals are all different, so the rank and the row order are the query's promise"
        delivered.collect { dec(it.t) }.toSet().size() == 25

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] why the filter is written twice: with the outer WHERE only, the brackets average what was ORDERED and 5 pass"() {
        // case-seven's second and third turns, and the article's "Why the filter is written twice".
        // The query is the lesson's own script with the inner WHERE removed — so this asserts exactly
        // the mistake the slide names, not a look-alike.
        given:
        def src = script("customers-above-the-average-customer-delivered")
        def outerOnly = src.replaceAll(/(?m)^[ \t]*WHERE o2\."ShippedDate" IS NOT NULL[ \t]*\r?\n/, '')
        def rows = sqlFor(engine).rows(outerOnly)

        expect: "the inner WHERE really was removed, and only that"
        outerOnly != src
        outerOnly.count('"ShippedDate" IS NOT NULL') == 1

        and: "the brackets without it return the average ordered customer, 2326.13"
        dec(sqlFor(engine).firstRow(script("average-customer")).values().first()) == dec("2326.13")

        and: "five customers pass, with no error: the top five of the seven"
        rows*.CompanyName == ["Lehmanns Marktstand", "Ernst Handel", "Alfreds Futterkiste",
                              "Frankenversand", "Morgenstern Gesundkost"]

        where:
        engine << ENGINES
    }

    // --- 5. What the KOANS stand on --------------------------------------------------------------

    @Unroll
    def "[#engine] the koan comments' facts are true"() {
        expect: "koan 1: the average freight is 50.49 and 36 orders sit above it"
        dec(sqlFor(engine).firstRow('SELECT ROUND(AVG("Freight"), 2) AS a FROM "Orders"').a) == dec("50.49")

        and: "koan 3: Janet (3) took twenty-seven orders, and two orders beat all of them — neither hers"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "EmployeeID" = 3').n == 27
        sqlFor(engine).rows('''SELECT "EmployeeID" AS e FROM "Orders"
                               WHERE "Freight" > (SELECT MAX("Freight") FROM "Orders" WHERE "EmployeeID" = 3)''')
                .every { it.e != 3 }

        and: "koan 4 and 5: Alfreds bought 8 different products on 11 order lines"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Order Details" d
                                   JOIN "Orders" o ON o."OrderID" = d."OrderID"
                                   WHERE o."CustomerID" = 'ALFKI' ''').n == 11

        and: "koan 6: without the GROUP BY the 'average supplier' is the whole business, 58153.31"
        dec(sqlFor(engine).firstRow('''SELECT ROUND(AVG(t."Total"), 2) AS a
                                       FROM (SELECT SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
                                             FROM "Products" p
                                             JOIN "Order Details" d ON d."ProductID" = p."ProductID") AS t''').a) == dec("58153.31")

        and: "koan 7: against the average order line, all six suppliers would pass"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM (
                                     SELECT p."SupplierID" FROM "Products" p
                                     JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                     GROUP BY p."SupplierID"
                                     HAVING SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount"))
                                          > (SELECT AVG("UnitPrice" * "Quantity" * (1 - "Discount")) FROM "Order Details")) t''').n == 6

        and: "koan 8: the two most expensive products are both Meat/Poultry"
        sqlFor(engine).rows('''SELECT c."CategoryName" AS c FROM "Products" p
                               JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
                               ORDER BY p."UnitPrice" DESC LIMIT 2''')*.c == ["Meat/Poultry", "Meat/Poultry"]

        and: "koan 9: the average product earned 2907.67"
        dec(sqlFor(engine).firstRow('''SELECT ROUND(AVG(t), 2) AS a FROM (
                                         SELECT SUM("UnitPrice" * "Quantity" * (1 - "Discount")) AS t
                                         FROM "Order Details" GROUP BY "ProductID") x''').a) == dec("2907.67")

        and: "koan 10: the cheapest product is one product, so '= (SELECT ...)' is safe there"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Products"
                                   WHERE "UnitPrice" = (SELECT MIN("UnitPrice") FROM "Products")''').n == 1

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
        titles[0] == "a value in brackets: freight above the average"
        titles[1] == "a value in brackets can be the whole comparison"
        titles[2] == "one value only: turn a column of values into one"
        koanQueries(titles[8])*.trim() == ["___"]
        koanQueries(titles[9])*.trim() == ["___"]
    }

    def "the video's editor mock quotes koan 1 verbatim"() {
        // ED_CODE and animFill in the video file hard-code koan 1's blank line; if the koan moves,
        // the mock would draw a line the file no longer has.
        expect:
        koanBody("a value in brackets: freight above the average")
                .contains('WHERE "Freight" > (SELECT ___("Freight") FROM "Orders")')
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static String script(String name) {
        new File("../courses/learnsql/series2-intermediate/00-subqueries/scripts/${name}.sql").text
    }

    /** A script's lines with trailing whitespace (and any CR) removed, for a line-by-line comparison. */
    private static List<String> lines(String s) {
        s.readLines().collect { it.replaceAll(/\s+$/, '') }.findAll { it }
    }

    private static String koansSource() {
        new File("src/koans/groovy/datazeus/learnsql/series2/_00/SubqueriesKoans.groovy").text
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
            "a value in brackets: freight above the average"                     : ["AVG"],
            "a value in brackets can be the whole comparison"                     : ['SELECT MAX("Freight") FROM "Orders"'],
            "one value only: turn a column of values into one"                    : ['MAX("Freight")'],
            "a list: IN"                                                          : ["IN"],
            "a join repeats what IN does not"                                     : ['d."ProductID" = p."ProductID"'],
            "a table in FROM, and its grain"                                      : ['GROUP BY p."SupplierID"'],
            "the average of what: suppliers above the average supplier"           : ['t."Total"'],
            "a subquery beside HAVING"                                            : ['AVG("UnitPrice")'],
            "write the whole query: products that beat the average product"      : ['''
                SELECT p."ProductName",
                       ROUND(SUM(d."UnitPrice" * d."Quantity"
                         * (1 - d."Discount")), 2) AS "Total sales"
                FROM "Products" p
                JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                GROUP BY p."ProductName"
                HAVING SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount"))
                     > (SELECT AVG(t."Total")
                        FROM (SELECT "ProductID",
                                     SUM("UnitPrice" * "Quantity" * (1 - "Discount")) AS "Total"
                              FROM "Order Details"
                              GROUP BY "ProductID") AS t)
                ORDER BY "Total sales" DESC
            '''],
            "write the whole query: a value inside a list inside a query"         : ['''
                SELECT "CustomerID", "CompanyName"
                FROM "Customers"
                WHERE "CustomerID" IN (
                  SELECT o."CustomerID"
                  FROM "Orders" o
                  JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                  WHERE d."ProductID" = (SELECT "ProductID" FROM "Products"
                                         WHERE "UnitPrice" = (SELECT MIN("UnitPrice") FROM "Products"))
                )
                ORDER BY "CustomerID"
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
