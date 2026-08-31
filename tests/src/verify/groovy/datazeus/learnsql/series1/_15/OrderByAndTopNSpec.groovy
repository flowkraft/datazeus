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
 *
 * AND THEN THE KOANS, ALL TEN, in their own section at the bottom. They deliberately do NOT
 * reuse the lesson's queries — they sort orders by freight and the warehouse by stock, where
 * the lesson sorts the catalogue by price — so none of the assertions above touches the data
 * they stand on. Every koan is checked here in its solved form, on both engines, plus the two
 * claims their HINTS make: what the unsolved koan 5 returns, and that koan 8's two engines
 * really do disagree until you write the NULLS clause.
 */
class OrderByAndTopNSpec extends NorthwindGateSpec {

    // --- 0. "A table has no order" — the claim the episode opens on ------------------------

    @Unroll
    def "[#engine] the three employees come back with no promised order, and there are three of them"() {
        given: "the lesson-02 query the video and the article both call back to"
        def rows = sqlFor(engine).rows('SELECT "FirstName", "LastName", "Title" FROM "Employees"')

        expect: "the same three people, in whatever order the engine hands them back"
        // ORDER-INDEPENDENT ON PURPOSE, and this is the episode's own point turned on itself:
        // the query has no ORDER BY, so pinning a row order here would assert the very thing
        // the lesson spends fourteen minutes telling you not to rely on.
        //
        // THE SPECIFIC ORDERS THE SLIDE QUOTES — DuckDB "Nancy, Andrew, Janet" and PostgreSQL
        // "Andrew, Janet, Nancy" — are lesson 05's to prove, and SelectFetchYourDataSpec
        // carries the caveat: they were verified against the live DataPallas PostgreSQL, and
        // whether the gate's PostgreSQL reproduces them depends on how it was seeded. What is
        // true on every engine, and all this lesson actually needs, is that three employees
        // come back and nothing promised an order.
        rows.size() == 3
        rows*.FirstName.toSet() == ["Nancy", "Andrew", "Janet"].toSet()

        where:
        engine << ENGINES
    }

    def "the dataset is the small Northwind the lesson quotes: 20 products, 25 customers, 79 orders"() {
        // Every "twenty-five customers" and "seventy-nine orders" in the article and on the
        // slides resolves to these three numbers. This is the SMALL Northwind, not the 91-
        // customer original, so looking an answer up elsewhere gives a different one.
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers"').n == 25 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79
        }
    }

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

    // --- 10. What the KOANS stand on ------------------------------------------------------
    //
    // THE KOANS DO NOT REUSE THE LESSON'S QUERIES. The article sorts the product catalogue by
    // price; the koans sort ORDERS by what they cost us to ship, and then the warehouse by
    // what is left on the shelf. That is the house convention — pom.xml states it as "the
    // koans are related practice, not a blanked copy of the gate" — and it exists so a learner
    // applies the idea somewhere new instead of retyping a query they just watched.
    //
    // Which is exactly why the koans need their own assertions. Nothing in the sections above
    // touches "Freight", "UnitsInStock", "ShipCountry" or "Discontinued", so a shift in that
    // data would surface as a RED KOAN ON A STUDENT'S SCREEN with a green gate behind it —
    // the worst possible place to discover it.
    //
    // Every koan is asserted with the SQL its solved form produces, on BOTH engines. The koans
    // run on DuckDB only, but each is written to give the same answer in CloudBeaver against
    // PostgreSQL; if that stops being true, a learner checking their work is told they are
    // wrong when they are right. Koan 8 is the deliberate exception and is handled below.

    @Unroll
    def "[#engine] koans 1 and 2: freight runs from order 8 at 10.0000 up to order 72 at 98.9200"() {
        given: "koan 1 — ascending is the default, so this is the cheapest delivery we ever paid for"
        def cheapest = sqlFor(engine).firstRow(
                'SELECT "OrderID" AS id, "Freight" AS f FROM "Orders" ORDER BY "Freight" LIMIT 1')

        and: "koan 2 — the same column, DESC"
        def dearest = sqlFor(engine).firstRow(
                'SELECT "OrderID" AS id, "Freight" AS f FROM "Orders" ORDER BY "Freight" DESC LIMIT 1')

        expect:
        cheapest.id == 8
        dec(cheapest.f) == dec("10.0000")
        dearest.id == 72
        dec(dearest.f) == dec("98.9200")

        and: "BOTH ENDS ARE UNIQUE — no tie, so LIMIT 1 has exactly one right answer and the"
        and: "two koans cannot go intermittently red on a student who wrote them correctly"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "Freight" = 10.0000').n == 1
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "Freight" = 98.9200').n == 1

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koans 3, 4 and 5: the five dearest deliveries, three ways, all the same five"() {
        given: "koan 3 — ORDER BY + LIMIT"
        def viaLimit = sqlFor(engine).rows(
                'SELECT "OrderID" FROM "Orders" ORDER BY "Freight" DESC LIMIT 5')

        and: "koan 4 — the standard spelling of the same idea"
        def viaFetch = sqlFor(engine).rows(
                'SELECT "OrderID" FROM "Orders" ORDER BY "Freight" DESC FETCH FIRST 5 ROWS ONLY')

        expect: "the answer all three koans are checked against"
        viaLimit*.OrderID == [72, 59, 46, 33, 20]
        viaFetch*.OrderID == [72, 59, 46, 33, 20]

        and: "no tie anywhere near the cut, so the list cannot wobble between runs"
        // Its own query on purpose: the koans SELECT "OrderID" alone, so the freight values
        // are simply not in the rows above to be checked.
        sqlFor(engine).rows('SELECT "Freight" FROM "Orders" ORDER BY "Freight" DESC LIMIT 6')
                .collect { dec(it.Freight) }.toSet().size() == 6

        where:
        engine << ENGINES
    }

    def "koan 5's UNSOLVED form really does mislead, and its hint's number is right"() {
        // The koan's comment tells the student what to expect if they run it before fixing it:
        // "orders 1 to 5, and order 5 at 11.6100 is the third CHEAPEST freight bill in the
        // whole table". A WRONG NUMBER IN A HINT IS WORSE THAN NO HINT — it tells a student
        // their correct query is wrong. So the claim is checked here.
        //
        // NOT @Unroll'd across ENGINES, and this is the same reasoning as the trap slide above:
        // a query with no ORDER BY has no promised row order, so "orders 1 to 5" is a recording
        // of what DuckDB happens to return — and DuckDB is the only engine the koans run on.
        // If it ever changes, the hint is wrong and this is what says so.
        given:
        def hurried = sqlFor("duckdb").rows('SELECT "OrderID", "Freight" FROM "Orders" LIMIT 5')

        expect: "the five rows the student sees before they fix it"
        hurried*.OrderID == [1, 2, 3, 4, 5]

        and: "and order 5's freight really is the third cheapest of all 79"
        dec(hurried.find { it.OrderID == 5 }.Freight) == dec("11.6100")
        sqlFor("duckdb").rows('SELECT "OrderID" FROM "Orders" ORDER BY "Freight" LIMIT 3')*.OrderID == [8, 21, 5]

        and: "THE POINT: the real top five is nowhere in it"
        !(72 in hurried*.OrderID)
    }

    @Unroll
    def "[#engine] koan 6: two products really are at zero stock, so the tie is real"() {
        given: "the koan's solved form — stock ascending, ties broken by name"
        def rows = sqlFor(engine).rows('''SELECT "ProductName" FROM "Products"
                                          ORDER BY "UnitsInStock", "ProductName" LIMIT 5''')

        expect:
        rows*.ProductName == ["Gorgonzola Telino", "Thuringer Rostbratwurst", "Scottish Longbreads",
                              "Aniseed Syrup", "Uncle Bobs Organic Dried Pears"]

        and: "THE TIE IS REAL — without the second key nothing chooses between these two"
        sqlFor(engine).rows('SELECT "ProductName" FROM "Products" WHERE "UnitsInStock" = 0')*.ProductName.toSet() ==
                ["Gorgonzola Telino", "Thuringer Rostbratwurst"].toSet()

        and: "and G before T is what the koan's hint tells the student to look for"
        rows[0].ProductName < rows[1].ProductName

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 7: ShipCountry groups the report, freight orders each country"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT "OrderID", "ShipCountry", "Freight" FROM "Orders"
                                          ORDER BY "ShipCountry", "Freight" DESC LIMIT 6''')

        expect:
        rows*.OrderID == [15, 40, 65, 18, 43, 68]

        and: "the FIRST key decides: Argentina's three, then Austria's three, never interleaved"
        rows*.ShipCountry == ["Argentina", "Argentina", "Argentina", "Austria", "Austria", "Austria"]

        and: "the second only speaks inside a country, and it speaks DESC"
        def argentina = rows.findAll { it.ShipCountry == "Argentina" }.collect { dec(it.Freight) }
        argentina == argentina.sort(false).reverse()

        and: "PLAIN ASCII COUNTRY NAMES on purpose — these sort identically under any collation,"
        and: "where a name with an umlaut would not and the koan would fail only on one engine"
        rows*.ShipCountry.every { it ==~ /[A-Za-z ]+/ }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 8 SOLVED: NULLS FIRST puts the backlog on top, identically on both engines"() {
        given: "the koan's solved form — and the second key is there because all 27 NULLs tie"
        def rows = sqlFor(engine).rows('''SELECT "OrderID", "ShippedDate" FROM "Orders"
                                          ORDER BY "ShippedDate" DESC NULLS FIRST, "OrderID" LIMIT 3''')

        expect:
        rows*.OrderID == [2, 5, 7]

        and: "every one of them is an order that has never shipped"
        rows.every { it.ShippedDate == null }

        where:
        engine << ENGINES
    }

    def "koan 8 UNSOLVED: the engines really do disagree, which is the whole koan"() {
        // The koan's comment claims that WITHOUT the NULLS clause, PostgreSQL puts the missing
        // ones first and DuckDB puts them last. That claim IS the exercise, so it is asserted —
        // per engine, because the two answers differing is the thing being proven.
        given: "the same query with the blank simply left out"
        String unsolved = '''SELECT "OrderID", "ShippedDate" FROM "Orders"
                             ORDER BY "ShippedDate" DESC, "OrderID" LIMIT 3'''

        expect: "DuckDB sorts missing LAST, so you get three orders that really shipped"
        sqlFor("duckdb").rows(unsolved).every { it.ShippedDate != null }

        and: "PostgreSQL treats NULL as larger than any date, so DESC hands you the backlog"
        sqlFor("postgres").rows(unsolved).every { it.ShippedDate == null }
    }

    @Unroll
    def "[#engine] koan 9: OFFSET 5 is deliveries six to ten, and it repeats nothing from page one"() {
        given:
        def pageOne = sqlFor(engine).rows('SELECT "OrderID" FROM "Orders" ORDER BY "Freight" DESC LIMIT 5')
        def pageTwo = sqlFor(engine).rows('SELECT "OrderID" FROM "Orders" ORDER BY "Freight" DESC LIMIT 5 OFFSET 5')

        expect:
        pageTwo*.OrderID == [71, 58, 45, 32, 19]

        and: "no order appears on both pages — only true because the sort is stable"
        pageOne*.OrderID.intersect(pageTwo*.OrderID).isEmpty()

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 10: the three lines the warehouse should reorder first"() {
        given: "the whole query the student writes from scratch: WHERE + two sort keys + LIMIT"
        def rows = sqlFor(engine).rows('''SELECT "ProductName" FROM "Products"
                                          WHERE "Discontinued" = false
                                          ORDER BY "UnitsInStock", "ProductName" LIMIT 3''')

        expect:
        rows*.ProductName == ["Gorgonzola Telino", "Scottish Longbreads", "Aniseed Syrup"]

        and: "THE WHERE HAS TO MATTER — Thuringer Rostbratwurst is at zero stock too and is"
        and: "second in koan 6, so a student who forgets the filter gets a visibly different list"
        sqlFor(engine).firstRow('''SELECT "Discontinued" AS d FROM "Products"
                                   WHERE "ProductName" = 'Thuringer Rostbratwurst' ''').d

        and: "exactly two discontinued lines, so the filter removes something and not everything"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "Discontinued"').n == 2

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
