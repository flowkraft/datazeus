package datazeus.learnsql.series1._15

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _15 "ORDER BY, LIMIT & FETCH FIRST".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted
 * here, on BOTH engines. The lesson's fourteen scripts:
 *
 *    1. products-by-price              — ascending is the default: 4.5000 leads
 *    2. products-by-price-desc         — DESC: 123.7900, then 97.0000, then the drop to 38
 *    3. postal-codes-text-sort         — text sorts like a dictionary: Graz 8010 in the middle
 *    4. top-5-expensive                — sort, then cut
 *    5. top-5-expensive-fetch-first    — the standard spelling, the same five rows
 *    6. five-rows-no-order             — THE TRAP: five rows that are not a top five
 *    7. cheapest-5-tie                 — a tie the query does not break
 *    8. cheapest-5-tiebreak            — the second sort key that fixes it
 *    9. customers-by-country-then-name — two keys on a list a human reads
 *   10. recent-shipments               — where NULL sorts, and the engines DISAGREE
 *   11. recent-shipments-nulls-last    — the same question, asked properly
 *   12. unshipped-backlog              — WHERE + ORDER BY: the query worth running
 *   13. products-page-2                — OFFSET, page two
 *   14. recent-orders                  — the hands-on query
 *
 * TWO ASSERTIONS HERE ARE DELIBERATELY NOT "the same on both engines", and they are the two
 * the lesson is built on:
 *
 *   THE TRAP (script 6). A result with no ORDER BY has NO promised order, so pinning the five
 *   rows it returns would assert something SQL does not guarantee — and the lesson's whole
 *   claim is that you must not rely on it. What is asserted instead is the part that is true
 *   on any engine, for any row order: those five rows are NOT the five most expensive, and the
 *   real top product is absent from them. That is the bug, stated as a property.
 *   Separately, and pinned to DuckDB alone, is the exact list the VIDEO shows on screen.
 *
 *   NULL PLACEMENT (script 10). DuckDB sorts NULLs last in both directions; PostgreSQL treats
 *   NULL as larger than any value, so DESC puts them first. Both are legal — the standard
 *   leaves it to the implementation — and the whole point of the slide is that the two answers
 *   differ. So this is asserted PER ENGINE, and then script 11 is asserted identical on both,
 *   which is what makes "say NULLS LAST and the question stops depending on the engine" a
 *   claim the gate actually proves rather than a nice sentence.
 *
 * Convention: the spec runs the SAME *.sql files the lesson and the video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 */
class OrderByAndTopNSpec extends NorthwindGateSpec {

    // --- 1. ORDER BY: ascending is the default -------------------------------------------

    @Unroll
    def "[#engine] ORDER BY with no direction word sorts ascending"() {
        given:
        def rows = sqlFor(engine).rows(script("products-by-price"))

        expect: "the whole catalogue, cheapest first"
        rows.size() == 20
        rows.first().ProductName == "Guarana Fantastica"
        dec(rows.first().UnitPrice) == dec("4.5000")

        and: "and it really is sorted — every price at least the one before it"
        def prices = rows.collect { dec(it.UnitPrice) }
        prices == prices.sort(false)

        and: "the last row is the most expensive product we sell"
        rows.last().ProductName == "Thuringer Rostbratwurst"
        dec(rows.last().UnitPrice) == dec("123.7900")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] DESC reverses it — and the top two are the premium lines"() {
        given:
        def rows = sqlFor(engine).rows(script("products-by-price-desc"))

        expect:
        rows.size() == 20
        rows*.ProductName.take(3) == ["Thuringer Rostbratwurst", "Mishi Kobe Niku", "Gnocchi di nonna Alice"]

        and: "the drop the video points at: 123.79, 97, and then 38"
        rows.take(3).collect { dec(it.UnitPrice) } == [dec("123.7900"), dec("97.0000"), dec("38.0000")]

        and: "descending, all the way down"
        def prices = rows.collect { dec(it.UnitPrice) }
        prices == prices.sort(false).reverse()

        where:
        engine << ENGINES
    }

    // --- 2. Sorting is comparing, so the data types lesson still applies ------------------

    @Unroll
    def "[#engine] a TEXT column sorts like a dictionary, so Graz 8010 lands in the middle"() {
        given:
        def rows = sqlFor(engine).rows(script("postal-codes-text-sort"))

        expect: "five customers, ordered by a postal code that is text"
        rows*.PostalCode == ["04179", "12209", "70563", "8010", "80805"]

        and: "Graz is fourth — after Stuttgart, though 8010 is the smaller NUMBER"
        rows[3].City == "Graz"
        rows[2].City == "Stuttgart"

        and: "which is only surprising until you check the type: it is text on both engines"
        isText(typeOf(engine, "Customers", "PostalCode"))

        where:
        engine << ENGINES
    }

    // --- 3. Sort, then cut ---------------------------------------------------------------

    @Unroll
    def "[#engine] ORDER BY + LIMIT is the top five, and it is the same five every run"() {
        given:
        def rows = sqlFor(engine).rows(script("top-5-expensive"))

        expect:
        rows.size() == 5
        rows*.ProductName == ["Thuringer Rostbratwurst", "Mishi Kobe Niku",
                              "Gnocchi di nonna Alice", "Camembert Pierrot", "Ikura"]

        and: "no tie anywhere near the cut, so this list cannot wobble"
        rows.collect { dec(it.UnitPrice) } ==
                [dec("123.7900"), dec("97.0000"), dec("38.0000"), dec("34.0000"), dec("31.0000")]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] FETCH FIRST n ROWS ONLY returns exactly what LIMIT returned"() {
        expect: "the standard spelling and the popular one, same answer"
        sqlFor(engine).rows(script("top-5-expensive-fetch-first"))*.ProductName ==
                sqlFor(engine).rows(script("top-5-expensive"))*.ProductName

        where:
        engine << ENGINES
    }

    // --- 4. THE TRAP ---------------------------------------------------------------------

    @Unroll
    def "[#engine] LIMIT without ORDER BY is not a top five — asserted as a property, not a row order"() {
        given: "the query written in a hurry"
        def hurried = sqlFor(engine).rows(script("five-rows-no-order"))

        and: "and the question asked properly"
        def correct = sqlFor(engine).rows(script("top-5-expensive"))

        expect: "it returns five rows, no error, nothing to notice"
        hurried.size() == 5

        and: "THE BUG: it is not the top five. The most expensive product we sell is missing"
        hurried*.ProductName != correct*.ProductName
        !("Thuringer Rostbratwurst" in hurried*.ProductName)

        and: "and what it hands you instead reaches down into the cheap end of the catalogue —"
        and: "the cheapest row it returns is below the cheapest row of the real top five"
        hurried.collect { dec(it.UnitPrice) }.min() < correct.collect { dec(it.UnitPrice) }.min()

        and: "every row is a real product at its real price, which is why nobody catches it"
        def realPrices = sqlFor(engine).rows(script("products-by-price"))
                .collectEntries { [(it.ProductName as String): dec(it.UnitPrice)] }
        hurried.every { realPrices[it.ProductName as String] == dec(it.UnitPrice) }

        where:
        engine << ENGINES
    }

    def "the five rows the VIDEO shows for the trap are DuckDB's, and only DuckDB's"() {
        // NOT @Unroll'd across ENGINES, deliberately. An unordered result has no promised
        // order, so this is not a rule — it is a recording of what one engine happens to
        // return, and it exists for one reason: the video puts these five rows on screen and
        // they have to be real. If DuckDB's storage order ever changes, this fails and the
        // slide gets updated. PostgreSQL is free to answer differently and that is the point.
        expect:
        sqlFor("duckdb").rows(script("five-rows-no-order"))*.ProductName ==
                ["Chai", "Chang", "Aniseed Syrup", "Chef Antons Cajun Seasoning", "Scottish Longbreads"]

        and: "Aniseed Syrup at 10.0000 really is the third cheapest product in the catalogue"
        sqlFor("duckdb").rows(script("products-by-price"))*.ProductName.take(3) ==
                ["Guarana Fantastica", "Filo Mix", "Aniseed Syrup"]
    }

    // --- 5. The same defect, one level down: an unbroken tie -----------------------------

    @Unroll
    def "[#engine] two products really do cost 12.5000, so the fifth cheapest is undecided"() {
        given:
        def rows = sqlFor(engine).rows(script("cheapest-5-tie"))

        expect: "the first four are settled by price alone"
        rows*.ProductName.take(3) == ["Guarana Fantastica", "Filo Mix", "Aniseed Syrup"]

        and: "and the fifth is whichever of the two 12.5000 rows the engine felt like"
        rows[3].ProductName in ["Scottish Longbreads", "Gorgonzola Telino"]
        rows[4].ProductName in ["Scottish Longbreads", "Gorgonzola Telino"]
        rows[3].ProductName != rows[4].ProductName

        and: "THE TIE IS REAL — the two prices are equal to the last decimal place"
        dec(rows[3].UnitPrice) == dec("12.5000")
        dec(rows[4].UnitPrice) == dec("12.5000")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] a second sort key settles it, identically on every engine"() {
        given:
        def rows = sqlFor(engine).rows(script("cheapest-5-tiebreak"))

        expect: "G before S decides the tie, and now the answer is repeatable"
        rows*.ProductName == ["Guarana Fantastica", "Filo Mix", "Aniseed Syrup",
                              "Gorgonzola Telino", "Scottish Longbreads"]

        where:
        engine << ENGINES
    }

    // --- 6. Two keys on a list a human reads ---------------------------------------------

    @Unroll
    def "[#engine] country groups the list, name orders each country"() {
        given:
        def rows = sqlFor(engine).rows(script("customers-by-country-then-name"))

        expect:
        rows*.CompanyName == ["Cactus Comidas para llevar", "Ernst Handel", "Bon app'",
                              "Du monde entier", "Alfreds Futterkiste", "Blauer See Delikatessen",
                              "Die Wandernde Kuh", "Drachenblut Delikatessen"]

        and: "the FIRST key decides: countries are in order and never interleave"
        def countries = rows*.Country
        countries == countries.sort(false)

        and: "the second only speaks inside a country — France's two are alphabetical"
        rows.findAll { it.Country == "France" }*.CompanyName == ["Bon app'", "Du monde entier"]

        where:
        engine << ENGINES
    }

    // --- 7. Where "missing" sorts — and this is where the engines part company ------------

    def "27 of the 79 orders have never shipped, which is what makes the NULL slide real"() {
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "ShippedDate" IS NULL').n == 27
        }
    }

    def "duckdb sorts missing LAST, so DESC hands you the five real shipments"() {
        given:
        def rows = sqlFor("duckdb").rows(script("recent-shipments"))

        expect:
        rows*.OrderID == [6, 4, 79, 3, 78]

        and: "not a NULL among them"
        rows.every { it.ShippedDate != null }
    }

    def "postgres treats NULL as larger than any date, so DESC hands you five orders that never shipped"() {
        // THE SLIDE'S WHOLE CLAIM, and the reason it is worth a minute of the episode.
        // Same query, same data, an answer that is not merely in a different order but is
        // about DIFFERENT ORDERS — and neither engine is wrong. If PostgreSQL ever changes
        // this default, the video and the article are both wrong and this is what says so.
        given:
        def rows = sqlFor("postgres").rows(script("recent-shipments"))

        expect: "every row is an unshipped order"
        rows.size() == 5
        rows.every { it.ShippedDate == null }
    }

    @Unroll
    def "[#engine] NULLS LAST makes the two engines agree"() {
        given:
        def rows = sqlFor(engine).rows(script("recent-shipments-nulls-last"))

        expect: "the five most recently shipped orders, on any engine"
        rows*.OrderID == [6, 4, 79, 3, 78]
        rows*.CustomerID == ["AROUT", "ALFKI", "OTTIK", "BERGS", "MORGK"]
        rows.every { it.ShippedDate != null }

        where:
        engine << ENGINES
    }

    // --- 8. WHERE + ORDER BY: the query worth running ------------------------------------

    @Unroll
    def "[#engine] the backlog, oldest first — and the oldest is order 8, unshipped since 2022"() {
        given:
        def rows = sqlFor(engine).rows(script("unshipped-backlog"))

        expect:
        rows*.OrderID == [8, 11, 14, 17, 20]
        rows*.CustomerID == ["ALFKI", "AROUT", "BONAP", "DUMON", "FRANK"]

        and: "order 8 was placed on the 5th of December 2022 and has never shipped"
        (rows[0].OrderDate as String).startsWith("2022-12-05")

        where:
        engine << ENGINES
    }

    // --- 9. OFFSET, and the hands-on query ------------------------------------------------

    @Unroll
    def "[#engine] OFFSET 5 is page two: products six to ten, and it does not repeat page one"() {
        given:
        def pageOne = sqlFor(engine).rows(script("top-5-expensive"))
        def pageTwo = sqlFor(engine).rows(script("products-page-2"))

        expect:
        pageTwo*.ProductName == ["Uncle Bobs Organic Dried Pears", "Tofu",
                                 "Chef Antons Cajun Seasoning", "Queso Cabrales", "Ravioli Angelo"]

        and: "no row appears on both pages — which is only guaranteed because the sort is stable"
        pageOne*.ProductName.intersect(pageTwo*.ProductName).isEmpty()

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the five most recent orders — and OrderDate is never missing"() {
        given:
        def rows = sqlFor(engine).rows(script("recent-orders"))

        expect:
        rows*.OrderID == [7, 5, 6, 4, 79]

        and: "no NULLS clause needed here, and this is why"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "OrderDate" IS NULL').n == 0

        where:
        engine << ENGINES
    }

    // --- 10. The koans, pinned ------------------------------------------------------------

    @Unroll
    def "[#engine] koan 1: the cheapest product is Guarana Fantastica"() {
        expect:
        sqlFor(engine).firstRow('SELECT "ProductName" AS n FROM "Products" ORDER BY "UnitPrice" LIMIT 1').n ==
                "Guarana Fantastica"

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 3: the most expensive product costs 123.7900"() {
        expect:
        dec(sqlFor(engine).firstRow('SELECT "UnitPrice" AS p FROM "Products" ORDER BY "UnitPrice" DESC LIMIT 1').p) ==
                dec("123.7900")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 7: alphabetically, Alfreds Futterkiste is first and Toms Spezialitaten last"() {
        // TWENTY-FIVE customers, not the 91 of the full Northwind — so the answers here are
        // this dataset's, and looking them up elsewhere gives a different name. Both ends are
        // asserted because the koan asks for the LAST one, which is the DESC half of the pair.
        // Plain ASCII initials on purpose: "Toms" and "Alfreds" sort the same under every
        // collation, where a name starting with an umlaut would not.
        expect:
        sqlFor(engine).firstRow('SELECT "CompanyName" AS n FROM "Customers" ORDER BY "CompanyName" LIMIT 1').n ==
                "Alfreds Futterkiste"
        sqlFor(engine).firstRow('SELECT "CompanyName" AS n FROM "Customers" ORDER BY "CompanyName" DESC LIMIT 1').n ==
                "Toms Spezialitäten"

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 9: the heaviest freight bill is order 72, at 98.9200"() {
        given:
        def row = sqlFor(engine).firstRow('SELECT "OrderID" AS id, "Freight" AS f FROM "Orders" ORDER BY "Freight" DESC LIMIT 1')

        expect:
        row.id == 72
        dec(row.f) == dec("98.9200")

        where:
        engine << ENGINES
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/15-order-by-and-top-n/scripts/${name}.sql").text
    }

    /** Prices are DECIMAL(19,4) on DuckDB and numeric on PostgreSQL, and the two hand back
     *  different Java types with different scales. Compare by VALUE, never by toString or
     *  by ==, or 12.5 and 12.5000 stop being equal for reasons that have nothing to teach. */
    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }

    private String typeOf(String engine, String table, String column) {
        sqlFor(engine).firstRow("""SELECT "data_type" AS t FROM information_schema.columns
                                   WHERE table_name = ? AND "column_name" = ?""",
                [table, column]).t as String
    }

    /** Same loose family matcher episode 07 uses — widen it for a new engine, never narrow
     *  it to one spelling. PostgreSQL says "character varying" where DuckDB says VARCHAR. */
    private static boolean isText(String t) {
        t?.toLowerCase() in ["varchar", "text", "character varying", "char", "character"]
    }
}
