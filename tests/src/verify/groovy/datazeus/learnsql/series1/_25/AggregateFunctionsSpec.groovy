package datazeus.learnsql.series1._25

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

import java.math.RoundingMode

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _25 "COUNT, SUM, AVG, MIN, MAX".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted
 * here, on BOTH engines. The lesson's fifteen scripts:
 *
 *    1. count-orders              — many rows in, one number out: 79
 *    2. price-range               — MIN and MAX together: 4.5000 and 123.7900
 *    3. first-and-last-order      — MIN/MAX are not only for numbers: two dates
 *    4. total-freight             — SUM: 3988.5200 paid to couriers
 *    5. average-freight           — AVG, and every digit it has
 *    6. stock-value               — SUM over an expression: what the shelves are worth
 *    7. dearest-product           — 123.7900 … and nobody can buy it
 *    8. dearest-on-sale           — THE SAME QUESTION, ASKED PROPERLY: 38.0000
 *    9. catalogue-on-sale         — four aggregates over one filtered set
 *   10. orders-vs-shipped         — THE LESSON: 79 rows, 52 values
 *   11. average-freight-shipped   — the average when you say which rows you mean
 *   12. japan-orders              — no rows at all, and one row of answer anyway
 *   13. countries-we-ship-to      — count the DIFFERENT values: 10, not 79
 *   14. column-with-aggregate     — THE WALL: a plain column beside an aggregate. ERRORS.
 *   15. dearest-product-name      — how you name the row instead (ORDER BY + LIMIT 1)
 *
 * THE EPISODE'S SPINE, and what makes these assertions worth having: an aggregate is an
 * answer about a SET OF ROWS, and two things decide that set while being invisible in the
 * answer — WHICH ROWS survived WHERE, and WHICH VALUES were actually there.
 *
 *   WHERE (scripts 7 and 8). Both of the two most expensive products are DISCONTINUED, so
 *   max("UnitPrice") over the whole catalogue is 123.7900 — a price no customer can pay.
 *   Filter to what is still on sale and the same question answers 38.0000. That is a 69%
 *   drop with no error, no warning and no hint on screen, and it is the reason the slide
 *   exists. Both halves are asserted, and so is the fact that the two dearest lines really
 *   are the discontinued ones — if that ever changes, the whole act stops being true.
 *
 *   MISSING VALUES (scripts 10 and 11). count(*) is 79 and count("ShippedDate") is 52,
 *   because 27 orders have been placed and never shipped. Two correct answers to "how many
 *   orders", a third of the business apart. The curriculum brief calls this "the lesson,
 *   not a footnote", and everything about NULL beyond "an empty cell is not a value" is
 *   deliberately deferred to Series 1 · 45.
 *
 *   THE EMPTY SET (script 12). No order ever shipped to Japan, so WHERE throws all 79 rows
 *   away — and the query still returns exactly one row: count 0, sum NULL. Not zero. A
 *   report tile that expected a number gets a blank.
 *
 * TWO THINGS HERE ARE NOT ASSERTED AS "the same on both engines", and neither is a defect:
 *
 *   AVG PRECISION. PostgreSQL's avg over numeric returns numeric (50.4875949367088608);
 *   DuckDB's returns a double (50.48759493670886). Same value, different tail — so every
 *   average below is compared at FOUR DECIMAL PLACES via dec4(), which is well inside both
 *   engines' agreement and is also the precision the lesson actually claims. The article
 *   says so in as many words; do not "fix" this by pinning one engine's digits.
 *
 *   THE ERROR TEXT. script 14 must FAIL on both engines, and that is asserted — but the
 *   messages differ ("Binder Error" on DuckDB, SQLSTATE 42803 on PostgreSQL), so what is
 *   checked is that it throws and that the message names GROUP BY. The exact PostgreSQL
 *   wording the video's error panel draws is asserted separately, on PostgreSQL alone,
 *   because CloudBeaver is what the learner is typing into.
 *
 * Convention: the spec runs the SAME *.sql files the lesson and the video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 *
 * AND THEN THE KOANS, ALL TEN, in their own section at the bottom. They deliberately do NOT
 * reuse the lesson's queries — the lesson counts orders, totals the freight bill and works
 * the price list; the koans work the WAREHOUSE ("UnitsInStock"), the ORDER LINES ("Quantity")
 * and the CUSTOMER LIST — so none of the assertions above touches the data they stand on.
 * Every koan is checked here in its solved form, on both engines, plus the factual claims
 * their HINTS make: seven customers with no fax, nothing over 200, Germany the biggest market.
 */
class AggregateFunctionsSpec extends NorthwindGateSpec {

    // --- 0. The dataset the lesson quotes ------------------------------------------------

    def "the dataset is the small Northwind: 20 products, 25 customers, 79 orders, 193 order lines"() {
        // Every "twenty products", "twenty-five customers" and "seventy-nine orders" in the
        // article, on the slides and in the koan file's schema block resolves to these. This is
        // the SMALL Northwind, not the 91-customer original, so an answer looked up elsewhere
        // will be a different number.
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers"').n == 25 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Order Details"').n == 193
        }
    }

    // --- 1. Many rows in, one number out --------------------------------------------------

    @Unroll
    def "[#engine] count(*) collapses the whole table to a single row holding 79"() {
        given:
        def rows = sqlFor(engine).rows(script("count-orders"))

        expect: "ONE row, ONE column — the shape is half the lesson"
        rows.size() == 1
        rows[0].size() == 1
        rows[0].Orders == 79

        where:
        engine << ENGINES
    }

    // --- 2. The four that do arithmetic ---------------------------------------------------

    @Unroll
    def "[#engine] MIN and MAX bracket the price list: 4.5000 to 123.7900"() {
        given:
        def r = sqlFor(engine).firstRow(script("price-range"))

        expect:
        dec(r.Cheapest) == dec("4.5000")
        dec(r.Dearest) == dec("123.7900")

        and: "and they really are the ends of the column — the same two rows ORDER BY finds"
        def prices = sqlFor(engine).rows('SELECT "UnitPrice" FROM "Products"').collect { dec(it.UnitPrice) }
        prices.min() == dec("4.5000")
        prices.max() == dec("123.7900")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] MIN and MAX are not only for numbers — on a date column they are first and latest"() {
        given:
        def r = sqlFor(engine).firstRow(script("first-and-last-order"))

        expect: "the 5th of December 2022, and the 12th of June 2024"
        (r."First order" as String).startsWith("2022-12-05")
        (r."Latest order" as String).startsWith("2024-06-12")

        and: "no order is missing its date, so nothing here is being skipped"
        sqlFor(engine).firstRow('SELECT count("OrderDate") AS n FROM "Orders"').n == 79

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] SUM: 3988.5200 paid to the couriers across all 79 orders"() {
        expect:
        dec(sqlFor(engine).firstRow(script("total-freight"))."Total freight") == dec("3988.5200")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] AVG: 50.4876 per order — asserted to four places, because the engines differ after that"() {
        given:
        def avg = sqlFor(engine).firstRow(script("average-freight"))."Average freight"

        expect: "PostgreSQL says 50.4875949367088608, DuckDB says 50.48759493670886 — same number"
        dec4(avg) == dec("50.4876")

        and: "AVG really is the total over the number of values, and here that is every row"
        dec4(dec("3988.5200") / 79) == dec4(avg)

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] ROUND around the average gives 50.49 — the same 50.49 on both engines"() {
        // THE CALLBACK TO EPISODE 20, which taught ROUND and taught it as "you round the ANSWER,
        // not the input". Here the answer is an aggregate, so ROUND wraps the whole avg() call.
        // It also quietly settles the engine difference the slide above has to caveat: rounded
        // to two places the two engines print the identical string, which is the point of
        // rounding for a reader in the first place.
        expect:
        dec(sqlFor(engine).firstRow(script("average-freight-rounded"))."Average freight") == dec("50.49")

        and: "and it really is a rounding of the full-precision average, not a different sum"
        dec4(sqlFor(engine).firstRow(script("average-freight"))."Average freight")
                .setScale(2, RoundingMode.HALF_UP) == dec("50.49")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] SUM over an expression: the shelves hold 13771.5000 of stock"() {
        // The hands-on query, and the one place this lesson leans on episode 20: "UnitPrice"
        // times "UnitsInStock" is an expression, and SUM takes one exactly as it takes a column.
        expect:
        dec(sqlFor(engine).firstRow(script("stock-value"))."Stock value") == dec("13771.5000")

        where:
        engine << ENGINES
    }

    // --- 3. THE FIRST TRAP: WHERE decides the set before the aggregate ever runs -----------

    @Unroll
    def "[#engine] the dearest product is 123.7900 — and it is a line we no longer sell"() {
        given:
        def dearest = sqlFor(engine).firstRow(script("dearest-product")).Dearest

        expect:
        dec(dearest) == dec("123.7900")

        and: "THE WHOLE ACT: both of the two most expensive products are discontinued"
        def top2 = sqlFor(engine).rows(
                'SELECT "ProductName", "Discontinued" FROM "Products" ORDER BY "UnitPrice" DESC LIMIT 2')
        top2*.ProductName == ["Thuringer Rostbratwurst", "Mishi Kobe Niku"]
        top2.every { it.Discontinued }

        and: "and there are exactly two of them, so the filter removes something and not everything"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "Discontinued"').n == 2

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the reveal slide: the two dearest lines, with the column that spoils them"() {
        // The video puts these two rows on screen with "Discontinued" showing, because telling a
        // viewer "both of them are discontinued" is an assertion and showing the column is
        // evidence. Its own script rather than a slice of dearest-product, so the exact three
        // cells the slide draws are the ones the gate runs.
        given:
        def rows = sqlFor(engine).rows(script("two-dearest-discontinued"))

        expect:
        rows.size() == 2
        rows*.ProductName == ["Thuringer Rostbratwurst", "Mishi Kobe Niku"]
        rows.collect { dec(it.UnitPrice) } == [dec("123.7900"), dec("97.0000")]

        and: "BOTH true — one would make the point half as well and the slide would be wrong"
        rows.every { it.Discontinued == true }

        and: "and the THIRD dearest is not discontinued, which is why 38.0000 is the honest answer"
        def third = sqlFor(engine).firstRow(
                'SELECT "ProductName" AS n, "Discontinued" AS d FROM "Products" ORDER BY "UnitPrice" DESC LIMIT 1 OFFSET 2')
        third.n == "Gnocchi di nonna Alice"
        third.d == false

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the same question over the rows that matter answers 38.0000 instead"() {
        expect: "a 69% drop, from one added WHERE — and no error either time"
        dec(sqlFor(engine).firstRow(script("dearest-on-sale")).Dearest) == dec("38.0000")

        and: "38.0000 is Gnocchi di nonna Alice, the dearest thing a customer can actually order"
        sqlFor(engine).firstRow('''SELECT "ProductName" AS n FROM "Products"
                                   WHERE "Discontinued" = false
                                   ORDER BY "UnitPrice" DESC LIMIT 1''').n == "Gnocchi di nonna Alice"

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] four aggregates over one filtered set: 18 lines, 4.5000 to 38.0000, averaging 19.6444"() {
        given:
        def r = sqlFor(engine).firstRow(script("catalogue-on-sale"))

        expect:
        r."On sale" == 18
        dec(r.Cheapest) == dec("4.5000")
        dec(r.Dearest) == dec("38.0000")

        and: "the average moves too — 28.7195 over the whole catalogue, 19.6444 over what is on sale"
        dec4(r.Average) == dec("19.6444")
        dec4(sqlFor(engine).firstRow('SELECT avg("UnitPrice") AS a FROM "Products"').a) == dec("28.7195")

        and: "EVERY ONE OF THE FOUR describes the same 18 rows — that is what one SELECT buys you"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "Discontinued" = false').n == 18

        where:
        engine << ENGINES
    }

    // --- 4. THE SECOND TRAP: count(*) counts rows, count(column) counts values -------------

    @Unroll
    def "[#engine] 79 orders, 52 shipped dates — the same table, two right answers"() {
        given:
        def r = sqlFor(engine).firstRow(script("orders-vs-shipped"))

        expect:
        r.Orders == 79
        r.Shipped == 52

        and: "THE GAP IS REAL: 27 orders were placed and have never shipped"
        r.Orders - r.Shipped == 27
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "ShippedDate" IS NULL').n == 27

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] say which rows you mean and the average moves: 52 shipped orders at 51.1696"() {
        given:
        def r = sqlFor(engine).firstRow(script("average-freight-shipped"))

        expect:
        r."Shipped orders" == 52
        dec4(r."Average freight") == dec("51.1696")

        and: "and it is NOT the number the unfiltered query gave — which is the whole point"
        dec4(r."Average freight") != dec4(sqlFor(engine).firstRow(script("average-freight"))."Average freight")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the ARTICLE's denominator arithmetic: the same total over 79 is 50.4876, over 52 is 76.7023"() {
        // THE ARTICLE ONLY, not the video. It makes the AVG rule concrete by showing how far a
        // moved denominator can push an average: "AVG divides by the number of VALUES it found,
        // not the number of rows" is abstract until you see the same 3988.5200 divided two ways.
        //
        // "Freight" itself has no gaps — which is exactly why the article says the agreement
        // between avg("Freight") and sum/count(*) is a property of THIS COLUMN and not a rule.
        // The 52 is borrowed from "ShippedDate" to show the size of the effect, and the article
        // says so in as many words. Both figures are asserted because both are printed.
        given:
        def total = dec(sqlFor(engine).firstRow(script("total-freight"))."Total freight")

        expect:
        dec4(total / 79) == dec("50.4876")
        dec4(total / 52) == dec("76.7023")

        and: "and 52 is a real count off this table, not a number chosen to look dramatic"
        sqlFor(engine).firstRow('SELECT count("ShippedDate") AS n FROM "Orders"').n == 52

        where:
        engine << ENGINES
    }

    // --- 5. THE THIRD TRAP: an aggregate over no rows at all ------------------------------

    @Unroll
    def "[#engine] no orders to Japan: one row back, count 0 and sum NULL — not zero"() {
        given:
        def rows = sqlFor(engine).rows(script("japan-orders"))

        expect: "WHERE threw away all 79 rows, and there is still exactly one row of answer"
        rows.size() == 1
        rows[0].Orders == 0

        and: "COUNT says nothing matched. SUM says nothing AT ALL, which is a different thing"
        rows[0]."Total freight" == null

        and: "there really is no Japanese order — if one is ever added, the slide is wrong"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShipCountry" = 'Japan' ''').n == 0

        where:
        engine << ENGINES
    }

    // --- 6. Counting the different values -------------------------------------------------

    @Unroll
    def "[#engine] DISTINCT inside the brackets: 10 countries across 79 orders"() {
        given:
        def r = sqlFor(engine).firstRow(script("countries-we-ship-to"))

        expect:
        r.Countries == 10
        r.Orders == 79

        and: "and no order is missing a country, so the 79 is a clean row count too"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "ShipCountry" IS NULL').n == 0

        where:
        engine << ENGINES
    }

    // --- 7. THE WALL: a plain column beside an aggregate ----------------------------------

    @Unroll
    def "[#engine] a plain column beside an aggregate is REFUSED, and the message names GROUP BY"() {
        // The video draws this failing, so it has to keep failing. The two engines word it
        // differently — "Binder Error: column ... must appear in the GROUP BY clause" on DuckDB,
        // SQLSTATE 42803 on PostgreSQL — so what is asserted on both is the part that is the
        // lesson: it throws, and the database itself points at GROUP BY, which is episode 30.
        when:
        sqlFor(engine).rows(script("column-with-aggregate"))

        then:
        def e = thrown(Exception)
        e.message.toUpperCase().contains("GROUP BY")

        where:
        engine << ENGINES
    }

    def "the PostgreSQL error the video's panel draws is the real one, word for word"() {
        // NOT @Unroll'd across ENGINES on purpose: the learner types into CloudBeaver, which is
        // PostgreSQL, so the panel shows PostgreSQL's wording — SQLSTATE 42803, the message
        // below, and no hint. Read off postgres:16.2 on 2026-08-30. If a future PostgreSQL
        // rewords it, this fails and the slide gets updated rather than quietly lying.
        when:
        sqlFor("postgres").rows(script("column-with-aggregate"))

        then:
        def e = thrown(Exception)
        e.message.contains('column "Products.ProductName" must appear in the GROUP BY clause ' +
                           'or be used in an aggregate function')
    }

    @Unroll
    def "[#engine] ORDER BY + LIMIT 1 is how you NAME the row: Thuringer Rostbratwurst at 123.7900"() {
        given:
        def rows = sqlFor(engine).rows(script("dearest-product-name"))

        expect: "the answer max() could not give you, using only episode 15"
        rows.size() == 1
        rows[0].ProductName == "Thuringer Rostbratwurst"
        dec(rows[0].UnitPrice) == dec("123.7900")

        and: "and it agrees with the aggregate — same number, arrived at two ways"
        dec(rows[0].UnitPrice) == dec(sqlFor(engine).firstRow(script("dearest-product")).Dearest)

        and: "NO TIE AT THE TOP, so LIMIT 1 has exactly one right answer"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "UnitPrice" = 123.7900').n == 1

        where:
        engine << ENGINES
    }

    // --- 8. What the KOANS stand on -------------------------------------------------------
    //
    // THE KOANS DO NOT REUSE THE LESSON'S QUERIES. The lesson counts orders, totals the freight
    // bill and works over the price list; the koans work the WAREHOUSE ("UnitsInStock"), the
    // ORDER LINES ("Quantity") and the CUSTOMER LIST. That is the house convention — pom.xml
    // states it as "the koans are related practice, not a blanked copy of the gate" — and it
    // exists so a learner applies the idea somewhere new instead of retyping a query they just
    // watched.
    //
    // Which is exactly why the koans need their own assertions. Nothing in the sections above
    // touches "UnitsInStock", "Quantity", "Fax" or "Country", so a shift in that data would
    // surface as a RED KOAN ON A STUDENT'S SCREEN with a green gate behind it — the worst
    // possible place to discover it.
    //
    // Every koan is asserted with the SQL its solved form produces, on BOTH engines. The koans
    // run on DuckDB only, but each is written to give the same answer in CloudBeaver against
    // PostgreSQL; if that stops being true, a learner checking their work is told they are
    // wrong when they are right.

    @Unroll
    def "[#engine] koan 1: there are 25 customers, and that is not the 79 the hint warns about"() {
        expect:
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers"').n == 25

        and: "the hint says 79 is the ORDER table, so the two must stay different numbers"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 2: the warehouse runs from an empty shelf to 123 units"() {
        given:
        def r = sqlFor(engine).firstRow('''SELECT min("UnitsInStock") AS "Emptiest",
                                                  max("UnitsInStock") AS "Fullest"
                                           FROM "Products"''')

        expect:
        r.Emptiest == 0
        r.Fullest == 123

        and: "the hint's two claims: TWO products are at zero, and the fullest is far clear of the rest"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "UnitsInStock" = 0').n == 2
        sqlFor(engine).rows('SELECT "UnitsInStock" AS u FROM "Products" ORDER BY "UnitsInStock" DESC LIMIT 2')
                *.u == [123, 53]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 3: 2070 units across the 193 order lines"() {
        expect:
        sqlFor(engine).firstRow('SELECT sum("Quantity") AS n FROM "Order Details"').n == 2070

        and: "the hint says the average line is about ten units — 2070 over 193 is 10.7"
        dec4(sqlFor(engine).firstRow('SELECT avg("Quantity") AS a FROM "Order Details"').a) == dec("10.7254")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 4: 585 units over 20 lines is EXACTLY 29.25, on both engines"() {
        given:
        def r = sqlFor(engine).firstRow('''SELECT sum("UnitsInStock") AS "Units held",
                                                  avg("UnitsInStock") AS "Average shelf"
                                           FROM "Products"''')

        expect:
        r."Units held" == 585

        and: "THE KOAN EXPECTS 29.25 LITERALLY, so this has to divide evenly — 585 / 20"
        dec(r."Average shelf") == dec("29.25")
        dec("585") / 20 == dec("29.25")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 5: 11 German customers, and Germany really is the biggest market"() {
        expect:
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Customers"
                                   WHERE "Country" = 'Germany' ''').n == 11

        and: "the hint says 'comfortably our biggest' and 'under half of 25' — both are checked"
        sqlFor(engine).rows('''SELECT "Country", count(*) AS n FROM "Customers"
                               GROUP BY "Country" ORDER BY n DESC LIMIT 2''')*.n == [11, 2]
        11 < 25 / 2

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 6: 25 customers, 18 fax numbers — so SEVEN have none, as the schema block says"() {
        given:
        def r = sqlFor(engine).firstRow('''SELECT count(*) AS "Customers",
                                                  count("Fax") AS "With a fax"
                                           FROM "Customers"''')

        expect:
        r.Customers == 25
        r."With a fax" == 18

        and: "the koan file's schema note claims seven empties, and a wrong number in a hint tells"
        and: "a student their correct query is wrong"
        r.Customers - r."With a fax" == 7
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers" WHERE "Fax" IS NULL').n == 7

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 7: nothing costs over 200, so the count is 0 and the max is NULL"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT count(*) AS "Products",
                                                 max("UnitPrice") AS "Dearest"
                                          FROM "Products"
                                          WHERE "UnitPrice" > 200''')

        expect: "one row, and the koan's expectation is [[0, null]]"
        rows.size() == 1
        rows[0].Products == 0
        rows[0].Dearest == null

        and: "THE FILTER HAS TO EMPTY THE TABLE — the dearest thing we sell is 123.7900"
        dec(sqlFor(engine).firstRow('SELECT max("UnitPrice") AS m FROM "Products"').m) < dec("200")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 8: ten different countries in the customer list"() {
        expect:
        sqlFor(engine).firstRow('SELECT count(DISTINCT "Country") AS n FROM "Customers"').n == 10

        and: "the hint says 'well under half of 25', and no customer is missing a country"
        10 < 25 / 2
        sqlFor(engine).firstRow('SELECT count("Country") AS n FROM "Customers"').n == 25

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 9: the fullest shelf is Boston Crab Meat, and nothing ties with it"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT "ProductName", "UnitsInStock" FROM "Products"
                                          ORDER BY "UnitsInStock" DESC LIMIT 1''')

        expect:
        rows*.ProductName == ["Boston Crab Meat"]
        rows[0].UnitsInStock == 123

        and: "NO TIE AT THE TOP, so LIMIT 1 cannot go intermittently red on a correct answer"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "UnitsInStock" = 123').n == 1

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 10: 11 German customers, 8 of them with a fax"() {
        given: "the whole query the student writes from scratch: WHERE + two different counts"
        def rows = sqlFor(engine).rows('''SELECT count(*) AS "Customers",
                                                 count("Fax") AS "With a fax"
                                          FROM "Customers"
                                          WHERE "Country" = 'Germany' ''')

        expect:
        rows.size() == 1
        rows[0].Customers == 11
        rows[0]."With a fax" == 8

        and: "BOTH HALVES HAVE TO MATTER: forget the WHERE and you get 25/18, forget the second"
        and: "count and you never meet the trap the koan is about"
        rows[0].Customers != 25
        rows[0].Customers != rows[0]."With a fax"

        where:
        engine << ENGINES
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/25-aggregate-functions/scripts/${name}.sql").text
    }

    /** Prices are DECIMAL(19,4) on DuckDB and numeric on PostgreSQL, and the two hand back
     *  different Java types with different scales. Compare by VALUE, never by toString or
     *  by ==, or 12.5 and 12.5000 stop being equal for reasons that have nothing to teach. */
    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }

    /** AVERAGES NEED THEIR OWN COMPARISON, and this is the one thing about this lesson that
     *  cannot be handled by dec() above. avg() over a numeric column returns NUMERIC on
     *  PostgreSQL and DOUBLE on DuckDB:
     *
     *      avg("Freight")   postgres 50.4875949367088608   duckdb 50.48759493670886
     *      avg("UnitPrice") postgres 28.7195000000000000   duckdb 28.7195
     *
     *  Those are the same number printed to different precision, so stripTrailingZeros() is not
     *  enough — 50.4875949367088608 and 50.48759493670886 are genuinely different BigDecimals.
     *  Four places is far inside where the two engines agree, and it is also all the lesson
     *  ever claims: the article prints 50.4876 and says in as many words that the tail depends
     *  on the engine. Do NOT widen this to compare full precision, and do not pin one engine's
     *  digits — either way the gate starts failing for a reason no learner would care about. */
    private static BigDecimal dec4(Object v) {
        new BigDecimal(v.toString()).setScale(4, RoundingMode.HALF_UP)
    }
}
