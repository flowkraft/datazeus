package datazeus.learnsql.series1._50

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _50
 * "SELECT + JOIN + GROUP BY — Building Your First Real Report".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted
 * here, on BOTH engines. The lesson's ten scripts, and the sections that run them:
 *
 *    1. count-orders            — the grain you start from: 79 orders                    §1
 *    2. customers-and-orders    — one hop. Still 79 rows: the join added a column        §1
 *    3. add-the-order-lines     — two hops. 193 rows. THE CHECK FIRES                    §1
 *    4. sales-by-customer       — the money, one number per customer                     §2
 *    5. orders-counted-wrong    — THE BUG: count(o."OrderID") counts LINES, not orders   §3
 *    6. customer-sales-summary  — the report you CHECK: no LIMIT, all twenty-five rows   §3 §4 §6
 *    7. top-ten-customers       — the report you SEND: the same query, ending LIMIT 10   §4
 *    8. report-total            — the money total, through all three tables              §5
 *    9. total-a-second-way      — the same total, with no join at all                    §5 §7
 *   10. avg-order-value         — the column that makes the bug hurt                     §6
 *
 * §7 asserts every number the KOANS' comments state, and §8 runs the koans file itself, as
 * written, on both engines.
 *
 * SIX AND SEVEN ARE THE SAME QUERY AND THAT IS DELIBERATE. You check on everything and you
 * send the top ten, because a total you cannot see every row of is a total you cannot check.
 * Both are on screen — 6 on the code cards, 7 read back whole at the end and pasted into the
 * DataPallas mockup — so both are asserted, including what the LIMIT throws away.
 *
 * ── WHY THIS EPISODE HAS THREE TABLES, AND WHERE THE LINE WITH SERIES 2 IS ──────────────
 *
 * Episode 40 taught JOIN on TWO tables and deferred "three or more" to Series 2 · 00. This
 * episode uses THREE, deliberately and with the boundary drawn:
 *
 *   WHAT 50 DOES. It is the Series 1 PROJECT, and the report it builds is a real one: the top
 *     ten customers, generated at the end as an Excel file somebody could be sent. That report
 *     needs three tables because the customer's NAME, the ORDERS that are theirs and the MONEY
 *     are three hops apart. A project episode that pretended otherwise would be a fake project.
 *     It chains one more JOIN of exactly the shape 40 taught and spends its time on GRAIN and
 *     CHECKING — not on join mechanics, of which it teaches nothing new.
 *
 *   WHAT 50 DOES NOT DO. It does not teach multi-table joins as a topic: no join order, no
 *     chains longer than three, no discussion of what the optimiser does. Series 2 · 00 opens
 *     on exactly that, from the question this episode has just given the learner a reason to
 *     care about — what happens to your row count when you follow the data across three or
 *     more tables.
 *
 *   AND SERIES 2 · 06 IS STILL INTACT. Here the moving row count is HONEST: the grain changes
 *     from one row per order to one row per order line, which is where the money is, and the
 *     MONEY total is unaffected (asserted below, twice, by two routes). The bug it causes is
 *     confined to COUNTING — count(*) stops counting orders — and count(DISTINCT ...) fixes
 *     it. Series 2 · 06 owns the case DISTINCT cannot fix: a fan-out that double-counts the
 *     money itself. That is a genuine escalation, not a repeat.
 *
 * ── THE EARN: ONE SILENT WRONG NUMBER, AND THE CHECK THAT CATCHES IT ───────────────────
 *
 * `count(o."OrderID")` on a query whose rows are ORDER LINES says Alfreds Futterkiste placed
 * ELEVEN orders. They placed FIVE. Nothing errors. The revenue column beside it is correct to
 * the penny and the ranking is correct, which is what makes it survive review — and every
 * per-order number computed from it is roughly halved: average order value reads 348.95
 * instead of 767.69. Both numbers are asserted here.
 *
 * The habit that catches it is the one the curriculum asked this episode to teach: COUNT THE
 * ROWS BEFORE THE JOIN AND AFTER IT (79 → 79 → 193, asserted below), and GET THE TOTAL A
 * SECOND WAY (58153.31 through three tables, 58153.31 through none).
 *
 * ── ORDER-SENSITIVITY, AND WHAT IS AND IS NOT SAFE TO PIN ──────────────────────────────
 *
 * Every result the lesson SHOWS carries its own ORDER BY "Total sales" DESC, so pinning those
 * rows asserts something the query actually promises. The one place that would NOT be safe is
 * a tie in the money column — there is none: the 25 customer totals are all distinct (asserted
 * explicitly, so a future data change cannot make the slide's row order luck).
 *
 * ── DECIMALS AND THE ONE GENUINE ENGINE DIFFERENCE ─────────────────────────────────────
 *
 * Money is compared BY VALUE via dec(), never by toString: DuckDB hands back DECIMAL and
 * PostgreSQL numeric, with different scales, and 4567.8 must equal 4567.80.
 *
 * AVERAGE ORDER VALUE IS THE REAL DIFFERENCE, and it is a TYPE difference rather than a value
 * one. `ROUND(decimal / bigint, 2)` comes back DOUBLE on DuckDB and NUMERIC on PostgreSQL. What
 * each tool PRINTS differs again: CloudBeaver trims trailing zeros, so it shows 4567.8 and
 * 1522.6; the DuckDB CLI prints the DECIMAL total as 4567.80 and the DOUBLE average as 1522.6.
 * The video's result cards print both to two places (4567.80, 1522.60). Same numbers,
 * different rendering — the same class of thing episode 07 already warns about. Asserted by
 * value on both.
 */
class YourFirstRealReportSpec extends NorthwindGateSpec {

    // --- 0. The dataset the whole lesson quotes -------------------------------------------

    def "the dataset is the small Northwind the lesson quotes"() {
        // Every "seventy-nine orders", "twenty-five customers" and "a hundred and ninety-three
        // lines" in the article and on the slides resolves to these numbers. This is the SMALL
        // Northwind, not the 91-customer original, so looking an answer up elsewhere gives a
        // different one.
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers"').n == 25 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Order Details"').n == 193 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Suppliers"').n == 6
        }
    }

    @Unroll
    def "[#engine] every customer has ordered and every order has a customer — so no INNER JOIN here loses a row"() {
        // The lesson claims the report covers ALL twenty-five customers, and that the three
        // plain JOINs cannot drop anybody. That is only true because this Northwind is
        // referentially perfect AND has no customer sitting at zero orders. If either stops
        // being true, this goes red BEFORE the slides start lying — the intended order.
        expect:
        [
            'SELECT count(*) FROM "Orders" o WHERE NOT EXISTS (SELECT 1 FROM "Customers" c WHERE c."CustomerID" = o."CustomerID")',
            'SELECT count(*) FROM "Customers" c WHERE NOT EXISTS (SELECT 1 FROM "Orders" o WHERE o."CustomerID" = c."CustomerID")',
            'SELECT count(*) FROM "Order Details" d WHERE NOT EXISTS (SELECT 1 FROM "Orders" o WHERE o."OrderID" = d."OrderID")',
            'SELECT count(*) FROM "Orders" o WHERE NOT EXISTS (SELECT 1 FROM "Order Details" d WHERE d."OrderID" = o."OrderID")',
        ].every { sqlFor(engine).firstRow(it).values().first() == 0 }

        where:
        engine << ENGINES
    }

    // --- 1. CHECK ONE: count the rows before the join and after it -------------------------

    @Unroll
    def "[#engine] 79 orders, 79 rows after the first join — the join added a column, not a row"() {
        // The gentle half of the third-table introduction. One hop changes nothing: a customer
        // has many orders, but an order has exactly one customer, so joining "Customers" to
        // "Orders" is one-to-one FROM THE ORDER'S SIDE and the count holds at 79.
        expect:
        sqlFor(engine).firstRow(script("count-orders")).values().first() == 79
        sqlFor(engine).firstRow(script("customers-and-orders")).values().first() == 79

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the second hop moves the row count: 79 becomes 193"() {
        // THE BEAT THE WHOLE EPISODE TURNS ON. The number moving is not a bug — the grain has
        // changed from one row per ORDER to one row per ORDER LINE, which is where the money
        // is. It is only a bug if nobody noticed, because count(*) has silently stopped
        // counting orders. Stated as arithmetic so it cannot drift.
        given:
        def before = sqlFor(engine).firstRow(script("customers-and-orders")).values().first()
        def after = sqlFor(engine).firstRow(script("add-the-order-lines")).values().first()

        expect:
        before == 79
        after == 193

        and: "and 193 is simply how many order lines there are — the join neither lost nor"
        and: "invented one, which is the other half of the check"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Order Details"').n == 193

        where:
        engine << ENGINES
    }

    // --- 2. The money, one number per customer ---------------------------------------------

    @Unroll
    def "[#engine] sales by customer: twenty-five rows, Cactus Comidas first"() {
        given:
        def rows = sqlFor(engine).rows(script("sales-by-customer"))

        expect: "one row per customer, and every customer is in it"
        rows.size() == 25

        and: "the top six the video puts on screen"
        rows.take(6)*.CompanyName == ["Cactus Comidas para llevar", "Blauer See Delikatessen",
                                      "Frankenversand", "Lehmanns Marktstand", "Ernst Handel",
                                      "Alfreds Futterkiste"]
        rows.take(6).collect { dec(it["Total sales"]) } ==
                [dec("4567.80"), dec("4318.80"), dec("4267.27"), dec("4077.12"), dec("3902.78"),
                 dec("3838.43")]

        and: "AND THE SIXTH ROW IS WHY THIS SLIDE SHOWS SIX. 3838.43 is the figure Leo cites on"
        and: "the-bug-result as the one that did NOT move when the count broke — so it has to be"
        and: "ON SCREEN HERE FIRST, and it has to be the same number in both places. It is the"
        and: "same assertion made from the other end in the count-bug feature below; if these two"
        and: "ever disagree, the video is asking a viewer to verify against something they were"
        and: "never shown."
        dec(rows[5]["Total sales"]) == dec("3838.43")

        and: "and the bottom of the report, which the article prints in full"
        rows.last().CompanyName == "QUICK-Stop"
        dec(rows.last()["Total sales"]) == dec("691.28")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] no two customers share a total, so the slide's row order is not luck"() {
        // The results above are pinned by ROW ORDER, which is only honest while the sort key is
        // unique. Episode 15 taught that an ORDER BY with a live tie has an undecided row; this
        // asserts the lesson is not quietly standing on one.
        given:
        def totals = sqlFor(engine).rows(script("sales-by-customer")).collect { dec(it["Total sales"]) }

        expect:
        totals.size() == 25
        totals.toSet().size() == 25

        where:
        engine << ENGINES
    }

    // --- 3. THE BUG: count(col) after a fan-out counts lines, not orders --------------------

    @Unroll
    def "[#engine] count of the order id says Alfreds placed ELEVEN orders — they placed five"() {
        given:
        def wrong = sqlFor(engine).rows(script("orders-counted-wrong"))
        def right = sqlFor(engine).rows(script("customer-sales-summary"))

        expect: "THE SILENT WRONG NUMBER, exactly as the video draws it"
        wrong.find { it.CompanyName == "Alfreds Futterkiste" }.Orders == 11
        right.find { it.CompanyName == "Alfreds Futterkiste" }.Orders == 5

        and: "and eleven is the number of ORDER LINES Alfreds accounts for — the join's grain"
        sqlFor(engine).firstRow('''SELECT count(*) AS n
                                   FROM "Orders" o
                                   JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                                   WHERE o."CustomerID" = 'ALFKI' ''').n == 11
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "CustomerID" = 'ALFKI' ''').n == 5

        and: "WHAT MAKES IT SURVIVE REVIEW: the money column beside it is correct either way,"
        and: "and so is the ranking — only the count is wrong"
        wrong*.CompanyName == right*.CompanyName
        wrong.collect { dec(it["Total sales"]) } == right.collect { dec(it["Total sales"]) }

        and: "THE-BUG-RESULT CARD, ROW FOR ROW: the six rows the video prints, wrong count and all —"
        and: "8, 8, 7, 8, 7, 11 — beside the same six totals the fixed report shows"
        wrong.take(6).collect { [it.CompanyName, it.Orders as int, dec(it["Total sales"])] } ==
                [["Cactus Comidas para llevar", 8, dec("4567.80")],
                 ["Blauer See Delikatessen", 8, dec("4318.80")],
                 ["Frankenversand", 7, dec("4267.27")],
                 ["Lehmanns Marktstand", 8, dec("4077.12")],
                 ["Ernst Handel", 7, dec("3902.78")],
                 ["Alfreds Futterkiste", 11, dec("3838.43")]]

        and: 'THE COLUMN why-eleven PUTS ON SCREEN, value for value. The slide answers the'
        and: 'question the count(*) framing never did — the learner NAMED the column, so why'
        and: 'did it not count orders? — by showing what o."OrderID" actually holds after both'
        and: 'joins: eleven values, five of them different, because order 33 carries three'
        and: 'lines and the rest carry two. If this sequence ever changes, the card is a lie.'
        sqlFor(engine).rows('''SELECT o."OrderID" AS id
                               FROM "Customers" c
                               JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                               JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                               WHERE c."CustomerID" = 'ALFKI'
                               ORDER BY o."OrderID" ''')*.id == [1, 1, 4, 4, 8, 8, 33, 33, 33, 58, 58]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the wrong count inflates EVERY customer, not just the worst one"() {
        // The slide names Alfreds because Alfreds is the row on screen — the sixth of the six
        // the-bug-result prints, and the code episode 15 left hanging. NOT because it is the
        // worst: ANATR and BERGS are also 11 against 5, and AROUT's 10 against 4 is a bigger
        // ratio. The point is that the whole column is wrong, and the count below says so in
        // the only form that cannot be read as "one bad row": ALL TWENTY-FIVE are overstated,
        // because every order in this dataset carries at least two lines. Leo states that on
        // why-eleven — "every number in that column is a count of order lines, not orders" —
        // so this is the assertion standing behind that sentence.
        given:
        def wrong = sqlFor(engine).rows(script("orders-counted-wrong")).collectEntries { [it.CompanyName, it.Orders] }
        def right = sqlFor(engine).rows(script("customer-sales-summary")).collectEntries { [it.CompanyName, it.Orders] }

        expect:
        wrong.every { name, n -> n >= right[name] }
        wrong.count { name, n -> n > right[name] } == 25

        and: "and the totals of the two columns are the two numbers the lesson names: 193"
        and: "order lines against 79 real orders"
        wrong.values().sum() == 193
        right.values().sum() == 79

        where:
        engine << ENGINES
    }

    // --- 4. The report itself ---------------------------------------------------------------

    @Unroll
    def "[#engine] the customer sales summary is the report the lesson ships"() {
        given:
        def rows = sqlFor(engine).rows(script("customer-sales-summary"))

        expect: "twenty-five rows, one per customer, biggest spender first"
        rows.size() == 25

        and: "the top five, with their order counts — the slide's result card"
        rows.take(5).collect { [it.CompanyName, it.Orders as int, dec(it["Total sales"])] } ==
                [["Cactus Comidas para llevar", 3, dec("4567.80")],
                 ["Blauer See Delikatessen", 3, dec("4318.80")],
                 ["Frankenversand", 3, dec("4267.27")],
                 ["Lehmanns Marktstand", 3, dec("4077.12")],
                 ["Ernst Handel", 3, dec("3902.78")]]

        and: "THE PAYOFF, open since lesson 10: the customer who PLACES the most orders is not"
        and: "the customer who SPENDS the most. Alfreds places five and comes sixth."
        rows[5].CompanyName == "Alfreds Futterkiste"
        rows[5].Orders == 5
        rows.take(5).every { it.Orders < 5 }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the report you SEND is the top ten, and it is the same query with a LIMIT"() {
        // TWO SCRIPTS, ONE QUERY, AND THE DIFFERENCE IS THE LESSON'S LAST HABIT.
        // customer-sales-summary.sql has no LIMIT and is the one the two checks run against —
        // a total you cannot see every row of is a total you cannot check. top-ten-customers.sql
        // is the same query ending "ORDER BY \"Total sales\" DESC LIMIT 10" and is the one that
        // becomes a spreadsheet. Both are on screen; both have to be true.
        //
        // IT ALSO CLOSES A PROMISE EPISODE 40 MADE. Its hand-over listed "cut to the top few —
        // ORDER BY and LIMIT" as part of this lesson, and until this script existed no query in
        // episode 50 had a LIMIT anywhere in it.
        //
        // 35773.80 IS THE EXCEL FOOTER. The lesson's template (templates/top-ten-customers-
        // excel.html) totals the rows it PRINTED, so the generated file says the top ten's total —
        // not the whole book's 58153.31, which answers a different question.
        given:
        def ten = sqlFor(engine).rows(script("top-ten-customers"))
        def all = sqlFor(engine).rows(script("customer-sales-summary"))

        expect: "exactly ten rows — the LIMIT is doing something"
        ten.size() == 10

        and: "and they are the first ten of the unlimited report, in the same order: the LIMIT"
        and: "changes WHAT YOU SEE and nothing else"
        ten*.CompanyName == all.take(10)*.CompanyName
        ten.collect { dec(it["Total sales"]) } == all.take(10).collect { dec(it["Total sales"]) }

        and: "YOUR-REPORT PRINTS ALL TEN. Rows 1 to 6 are pinned in the features above; these are"
        and: "rows 7 to 10, orders and all"
        ten.drop(6).collect { [it.CompanyName, it.Orders as int, dec(it["Total sales"])] } ==
                [["Morgenstern Gesundkost", 3, dec("3289.13")],
                 ["Around the Horn", 4, dec("2701.58")],
                 ["Great Lakes Food Market", 3, dec("2683.28")],
                 ["LILA-Supermercado", 3, dec("2127.61")]]

        and: "the footer the generated spreadsheet prints — the sum of the ten rows ON the page"
        dec(ten.collect { dec(it["Total sales"]) }.sum()) == dec("35773.80")

        and: "AND WHAT THE CUT REMOVES, which is the point of the slide that shows it: eleventh"
        and: "place is Berglunds snabbköp on FIVE orders — more orders than NINE of the ten"
        and: "printed above them — and it is off the page"
        // NINE, NOT EIGHT, AND THE GATE CAUGHT THE FIRST DRAFT SAYING EIGHT. The ten printed
        // rows run 3,3,3,3,3,5,3,4,3,3 — only Alfreds matches Berglunds' five, so Berglunds
        // out-orders nine of them. The slide says "nine" for this reason; do not soften it
        // back without re-running this.
        all[10].CompanyName == "Berglunds snabbköp"
        all[10].Orders == 5
        dec(all[10]["Total sales"]) == dec("1952.66")
        ten.count { (it.Orders as int) < 5 } == 9
        ten.count { (it.Orders as int) >= 5 } == 1

        and: "and the twelfth, the other row on that slide"
        all[11].CompanyName == "Du monde entier"
        all[11].Orders == 3
        dec(all[11]["Total sales"]) == dec("1918.93")

        where:
        engine << ENGINES
    }

    // --- 5. CHECK TWO: get the total a second way ------------------------------------------

    @Unroll
    def "[#engine] the money total is the same through three tables and through none"() {
        // THE SECOND HABIT, and the reason it is worth doing even when it passes: the rows
        // multiplied on the way through the joins (79 -> 193) and the money did not. Knowing
        // which of those two things a join changes is the whole skill this episode teaches.
        given:
        def throughJoins = sqlFor(engine).firstRow(script("report-total")).values().first()
        def direct = sqlFor(engine).firstRow(script("total-a-second-way")).values().first()

        expect:
        dec(throughJoins) == dec("58153.31")
        dec(direct) == dec("58153.31")
        dec(throughJoins) == dec(direct)

        where:
        engine << ENGINES
    }

    // --- 6. Average order value: where the wrong count actually hurts -----------------------

    @Unroll
    def "[#engine] average order value tells Cactus and Alfreds apart"() {
        given:
        def rows = sqlFor(engine).rows(script("avg-order-value"))

        expect: "the two customers the lesson contrasts: rarely and big, against often and small"
        dec(rows.find { it.CompanyName == "Cactus Comidas para llevar" }["Avg order"]) == dec("1522.60")
        dec(rows.find { it.CompanyName == "Alfreds Futterkiste" }["Avg order"]) == dec("767.69")

        and: "the first six rows, which the article prints — compared as VALUES, because DuckDB"
        and: "hands back the DOUBLE 1522.6 where PostgreSQL hands back the numeric 1522.60"
        rows.take(6).collect { [it.CompanyName, it.Orders as int, dec(it["Avg order"])] } ==
                [["Cactus Comidas para llevar", 3, dec("1522.60")],
                 ["Blauer See Delikatessen", 3, dec("1439.60")],
                 ["Frankenversand", 3, dec("1422.42")],
                 ["Lehmanns Marktstand", 3, dec("1359.04")],
                 ["Ernst Handel", 3, dec("1300.93")],
                 ["Alfreds Futterkiste", 5, dec("767.69")]]

        and: "and the report is still twenty-five rows in the same order"
        rows.size() == 25
        rows*.CompanyName == sqlFor(engine).rows(script("customer-sales-summary"))*.CompanyName

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] with the wrong count, average order value is roughly halved and nothing says so"() {
        // The consequence beat. 348.95 against 767.69 for Alfreds — a number a sales director
        // reads and acts on, wrong by a factor of two, in a report whose revenue column is
        // correct to the penny.
        given:
        def wrong = sqlFor(engine).rows('''SELECT c."CompanyName" AS n,
                   ROUND(SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount"))
                         / count(o."OrderID"), 2) AS a
            FROM "Customers" c
            JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
            JOIN "Order Details" d ON d."OrderID" = o."OrderID"
            GROUP BY c."CompanyName"''')

        expect:
        dec(wrong.find { it.n == "Alfreds Futterkiste" }.a) == dec("348.95")
        dec(wrong.find { it.n == "Cactus Comidas para llevar" }.a) == dec("570.97")

        where:
        engine << ENGINES
    }

    // --- 7. What the KOANS stand on ---------------------------------------------------------
    //
    // THE KOANS DO NOT REUSE THE LESSON'S QUERIES. The lesson joins CUSTOMERS to ORDERS to
    // ORDER DETAILS and asks who spends the most; the koans join SUPPLIERS to PRODUCTS to
    // ORDER DETAILS and ask whose goods earn the most. That is the house convention — pom.xml
    // states it as "the koans are related practice, not a blanked copy of the gate" — and it
    // exists so a learner applies the idea somewhere new instead of retyping a query they just
    // watched.
    //
    // Which is exactly why the koans need their own assertions. Nothing in the sections above
    // touches "Suppliers" or "Products", so a shift in that data would surface as a RED KOAN ON
    // A STUDENT'S SCREEN with a green gate behind it — the worst possible place to discover it.
    // Every number a koan's comment states is asserted here, on BOTH engines. §8 then runs the
    // koans file itself, so the queries the learner actually sees are checked as well.
    //
    // HALF A CENT IS THE ONE VALUE A KOAN MUST NOT EXPECT. A DuckDB DOUBLE and a PostgreSQL
    // numeric can round an exact x.xx5 apart, so every value a koan expects is also asserted to
    // be off that boundary before rounding — see halfCent(). Two koans are shaped by it, and the
    // values that shaped them are pinned below so nobody "restores" the obvious version.

    @Unroll
    def "[#engine] koans 1 and 2: the supplier chain moves 6 to 20 to 193"() {
        expect: "koan 1 — six suppliers, twenty products, so one hop gives twenty rows"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Suppliers" s
                                   JOIN "Products" p ON p."SupplierID" = s."SupplierID"''').n == 20

        and: "koan 2 — the second hop moves it to 193, one row per order line"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Suppliers" s
                                   JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                                   JOIN "Order Details" d ON d."ProductID" = p."ProductID"''').n == 193

        and: "and the six / twenty the koan header states"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Suppliers"').n == 6
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20

        and: "the header's schema block: 13, 10 and 5 columns, and exactly two discontinued products"
        columns(engine, "Suppliers") == 13
        columns(engine, "Products") == 10
        columns(engine, "Order Details") == 5
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "Discontinued"').n == 2

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koans 3 and 9: what each supplier earned us, by name, then ranked with its product count"() {
        given:
        def byName = sqlFor(engine).rows('''SELECT s."CompanyName" AS n,
                                                   ROUND(SUM(d."UnitPrice" * d."Quantity"
                                                     * (1 - d."Discount")), 2) AS t
                                            FROM "Suppliers" s
                                            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                                            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                            GROUP BY s."CompanyName"
                                            ORDER BY s."CompanyName"''')
        def ranked = sqlFor(engine).rows('''SELECT s."CompanyName" AS n,
                                                   count(DISTINCT p."ProductID") AS things,
                                                   ROUND(SUM(d."UnitPrice" * d."Quantity"
                                                     * (1 - d."Discount")), 2) AS t
                                            FROM "Suppliers" s
                                            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                                            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                            GROUP BY s."CompanyName"
                                            ORDER BY t DESC''')

        expect: "koan 3 — six rows, by name"
        byName.collect { [it.n, dec(it.t)] } ==
                [["Exotic Liquids", dec("4465.75")],
                 ["Grandma Kellys Homestead", dec("5522.57")],
                 ["New Orleans Cajun Delights", dec("2862.30")],
                 ["Pasta Buttini s.r.l.", dec("9248.50")],
                 ["Pavlova Ltd", dec("18519.15")],
                 ["Tokyo Traders", dec("17535.04")]]

        and: "KOAN 3'S CHECKABLE FACT: the six totals add up to 58153.31, the same total the lesson's"
        and: "total-a-second-way gets from \"Order Details\" alone"
        dec(byName.collect { dec(it.t) }.sum()) == dec("58153.31")
        dec(sqlFor(engine).firstRow(script("total-a-second-way")).values().first()) == dec("58153.31")

        and: "koan 9 — the same six, biggest earner first, with how many different products each"
        ranked.collect { [it.n, it.things as int, dec(it.t)] } ==
                [["Pavlova Ltd", 3, dec("18519.15")],
                 ["Tokyo Traders", 5, dec("17535.04")],
                 ["Pasta Buttini s.r.l.", 4, dec("9248.50")],
                 ["Grandma Kellys Homestead", 3, dec("5522.57")],
                 ["Exotic Liquids", 3, dec("4465.75")],
                 ["New Orleans Cajun Delights", 2, dec("2862.30")]]

        and: "koan 9's checks: the product counts add up to 20, and no two suppliers share a total"
        ranked.sum { it.things as int } == 20
        ranked.collect { dec(it.t) }.toSet().size() == 6

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 4: a count of the product column counts its values, DISTINCT counts the different ones"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT s."CompanyName" AS n,
                                                 count(DISTINCT p."ProductID") AS things,
                                                 count(p."ProductID") AS vals,
                                                 count(*) AS lines
                                          FROM "Suppliers" s
                                          JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                                          JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                          GROUP BY s."CompanyName"
                                          ORDER BY s."CompanyName"''')

        expect: "the koan's six product counts"
        rows.collect { [it.n, it.things as int] } ==
                [["Exotic Liquids", 3], ["Grandma Kellys Homestead", 3],
                 ["New Orleans Cajun Delights", 2], ["Pasta Buttini s.r.l.", 4],
                 ["Pavlova Ltd", 3], ["Tokyo Traders", 5]]

        and: "THE HINT'S CLAIM: none above five"
        rows.every { (it.things as int) <= 5 }

        and: "NAMING THE COLUMN DOES NOT CHANGE WHAT COUNT COUNTS: count(p.\"ProductID\") finds one"
        and: "value per order line, exactly as many as the rows — Tokyo Traders 51, only 5 different"
        rows.every { (it.vals as int) == (it.lines as int) }
        (rows.find { it.n == "Tokyo Traders" }.vals as int) == 51
        (rows.find { it.n == "Tokyo Traders" }.things as int) == 5

        and: "and 'if yours run from 20 to 51': the wrong column's smallest and largest counts"
        rows.collect { it.vals as int }.min() == 20
        rows.collect { it.vals as int }.max() == 51
        rows.sum { it.vals as int } == 193

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 5: the six product counts add up to the different products on the order lines"() {
        expect: "koan 4's six counts — 3 + 3 + 2 + 4 + 3 + 5 — add up to 20"
        (sqlFor(engine).firstRow('''SELECT sum(c) AS n FROM (
                                        SELECT count(DISTINCT p."ProductID") AS c
                                        FROM "Suppliers" s
                                        JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                                        JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                        GROUP BY s."CompanyName") x''').n as int) == 20

        and: "and \"Order Details\" alone, with no join and no grouping, says the same"
        (sqlFor(engine).firstRow('''SELECT count(DISTINCT "ProductID") AS n
                                    FROM "Order Details"''').n as int) == 20

        and: "WHY THE TWO HAVE TO AGREE, as the koan says: every product has exactly one supplier,"
        and: "so no product can be counted under two of them"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Products" p
                                   WHERE p."SupplierID" IS NULL
                                      OR NOT EXISTS (SELECT 1 FROM "Suppliers" s
                                                     WHERE s."SupplierID" = p."SupplierID")''').n == 0

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 6: what one product earns Pavlova Ltd and Tokyo Traders, and the per-line average that hides it"() {
        // WHY TWO SUPPLIERS AND NOT ALL SIX. The obvious koan lists every supplier's average per
        // product, and one of them is Pasta Buttini s.r.l.: 9248.50 over 4 products, 2312.125 —
        // exactly half a cent (pinned at the end). The two chosen are the lesson's own contrast
        // on the supplier side: the biggest earner against the supplier with the most products,
        // nearly the same money, very different per product.
        given:
        def rows = sqlFor(engine).rows('''SELECT s."CompanyName" AS n,
                                                 count(DISTINCT p."ProductID") AS things,
                                                 count(*) AS lines,
                                                 ROUND(SUM(d."UnitPrice" * d."Quantity"
                                                   * (1 - d."Discount")), 2) AS t,
                                                 ROUND(SUM(d."UnitPrice" * d."Quantity"
                                                   * (1 - d."Discount")) / count(DISTINCT p."ProductID"), 2) AS per_product,
                                                 ROUND(SUM(d."UnitPrice" * d."Quantity"
                                                   * (1 - d."Discount")) / count(*), 2) AS per_line,
                                                 SUM(d."UnitPrice" * d."Quantity"
                                                   * (1 - d."Discount")) / count(DISTINCT p."ProductID") AS raw
                                          FROM "Suppliers" s
                                          JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                                          JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                          GROUP BY s."CompanyName"
                                          ORDER BY s."CompanyName"''')
        def pair = rows.findAll { it.n in ["Pavlova Ltd", "Tokyo Traders"] }

        expect: "the koan's two rows"
        pair.collect { [it.n, dec(it.per_product)] } ==
                [["Pavlova Ltd", dec("6173.05")], ["Tokyo Traders", dec("3507.01")]]

        and: "THE COMMENT'S NUMBERS: nearly the same money, on 3 and 5 products, over 25 and 51 lines"
        pair.collect { [dec(it.t), it.things as int, it.lines as int] } ==
                [[dec("18519.15"), 3, 25], [dec("17535.04"), 5, 51]]

        and: "and divided by count(*) instead — by the lines — both averages come out far too small"
        pair.collect { dec(it.per_line) } == [dec("740.77"), dec("343.82")]

        and: "neither expected average sits on half a cent before rounding"
        pair.every { !halfCent(it.raw) }

        and: "WHY NOT ALL SIX, pinned: Pasta Buttini's average is exactly 2312.125"
        def pasta = rows.find { it.n == "Pasta Buttini s.r.l." }
        dec(pasta.raw) == dec("2312.125")
        halfCent(pasta.raw)

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koans 7 and 8: the top six products, and the seventh the cut leaves out"() {
        // WHY SIX AND NOT TEN. The lesson cuts its report at ten, and the obvious koan would too.
        // But tenth place among the products is Genen Shouyu at 2245.175 before rounding —
        // exactly half a cent, and a koan must not expect one (pinned at the end). Six is also
        // the cleaner story for koan 8: the seventh product is on more orders than ANY of the six
        // above it, rather than more than most.
        given:
        def all = sqlFor(engine).rows('''SELECT p."ProductName" AS n,
                                                count(DISTINCT d."OrderID") AS o,
                                                ROUND(SUM(d."UnitPrice" * d."Quantity"
                                                  * (1 - d."Discount")), 2) AS t,
                                                SUM(d."UnitPrice" * d."Quantity"
                                                  * (1 - d."Discount")) AS raw
                                         FROM "Products" p
                                         JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                         GROUP BY p."ProductName"
                                         ORDER BY t DESC''')

        expect: "koan 7 — the top six, with their orders and their money"
        all.take(6).collect { [it.n, it.o as int, dec(it.t)] } ==
                [["Thuringer Rostbratwurst", 11, dec("16464.07")],
                 ["Mishi Kobe Niku", 8, dec("6867.60")],
                 ["Ikura", 10, dec("4338.45")],
                 ["Gnocchi di nonna Alice", 7, dec("3784.80")],
                 ["Tofu", 11, dec("3218.96")],
                 ["Uncle Bobs Organic Dried Pears", 7, dec("2430.00")]]

        and: "koan 8 — the seventh, the first one below the line"
        [all[6].n, all[6].o as int, dec(all[6].t)] == ["Chef Antons Cajun Seasoning", 13, dec("2352.90")]

        and: "THE CUT IS DECIDED BY THE DATA, NOT BY LUCK: all twenty product totals are different"
        all.size() == 20
        all.collect { dec(it.t) }.toSet().size() == 20

        and: "KOAN 7'S HINT: the top product earns more than twice what the second one does"
        dec(all[0].t) > dec(all[1].t) * 2

        and: "KOAN 8'S HINT: 13 orders, more than any product in the top six, and 77.10 short of sixth"
        all.take(6).every { (it.o as int) < 13 }
        dec(all[5].t) - dec(all[6].t) == dec("77.10")

        and: "no value koans 7 and 8 expect sits on half a cent before rounding"
        all.take(7).every { !halfCent(it.raw) }

        and: "WHY NOT TEN, pinned: tenth place is Genen Shouyu, at exactly 2245.175"
        all[9].n == "Genen Shouyu"
        dec(all[9].raw) == dec("2245.175")
        halfCent(all[9].raw)

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 10: the top three suppliers, with the lesson's bug waiting in the Orders column"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT s."CompanyName" AS n,
                                                 count(DISTINCT p."ProductID") AS things,
                                                 count(DISTINCT d."OrderID") AS orders,
                                                 count(d."OrderID") AS lines,
                                                 ROUND(SUM(d."UnitPrice" * d."Quantity"
                                                   * (1 - d."Discount")), 2) AS t
                                          FROM "Suppliers" s
                                          JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                                          JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                          GROUP BY s."CompanyName"
                                          ORDER BY t DESC''')
        def top = rows.take(3)

        expect: "the koan's three rows"
        top.collect { [it.n, it.things as int, it.orders as int, dec(it.t)] } ==
                [["Pavlova Ltd", 3, 25, dec("18519.15")],
                 ["Tokyo Traders", 5, 39, dec("17535.04")],
                 ["Pasta Buttini s.r.l.", 4, 24, dec("9248.50")]]

        and: "THE BUG THE COMMENT WARNS ABOUT: a plain count of \"OrderID\" counts lines — Tokyo"
        and: "Traders' 51 lines are 39 orders, and only Pavlova Ltd would look right"
        top.collect { it.lines as int } == [25, 51, 35]

        // NO CLAIM ABOUT WHAT THE ORDERS COLUMN ADDS UP TO, on purpose. Per supplier it does not
        // add up to the 79 orders (one order can buy from several suppliers), and the owner
        // ruled that out as an edge case the koans must not raise. Koan 5 checks PRODUCTS a
        // second way for exactly that reason: every product has one supplier, so that count does.

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] why koan 10 has no average column: DuckDB lets a column divide the names to its left, PostgreSQL refuses"() {
        // KOAN 10 WAS DESIGNED WITH "AVERAGE PER PRODUCT" AS ITS FOURTH COLUMN, and it cannot have
        // one. A learner writing that report from scratch reaches for the two names already on the
        // line — ROUND("Total sales" / "Products", 2) — and DuckDB accepts it with the right
        // numbers while PostgreSQL refuses it: a green koan for a query CloudBeaver rejects, the
        // one outcome a koan must never have. (Its third row would also have been Pasta Buttini's
        // 2312.125, half a cent — see koan 6.) This pins the asymmetry, so the reason stays
        // visible to whoever next thinks the report looks bare without it.
        given:
        def lateral = '''SELECT s."CompanyName",
                                count(DISTINCT p."ProductID") AS "Products",
                                ROUND(SUM(d."UnitPrice" * d."Quantity"
                                  * (1 - d."Discount")), 2) AS "Total sales",
                                ROUND("Total sales" / "Products", 2) AS "Avg per product"
                         FROM "Suppliers" s
                         JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                         JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                         GROUP BY s."CompanyName"
                         ORDER BY "Total sales" DESC
                         LIMIT 2'''

        expect:
        if (engine == "duckdb") {
            assert sqlFor(engine).rows(lateral).collect { dec(it["Avg per product"]) } ==
                    [dec("6173.05"), dec("3507.01")]
        } else {
            try {
                sqlFor(engine).rows(lateral)
                assert false, "PostgreSQL must refuse a SELECT column that uses another column's alias"
            } catch (Exception expected) {
                assert expected.message?.contains('"Total sales" does not exist')
            }
        }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the koans header's warning: a wrong capital letter in a quoted name runs on DuckDB and fails on PostgreSQL"() {
        // The koans header tells the learner to keep every name exactly as the schema spells it,
        // because DuckDB forgives "Total Sales" for "Total sales" and PostgreSQL does not. That is
        // the one DuckDB leniency no koan with a name in its blank can design out (koans 2, 5, 6,
        // 7 and the two whole queries), so the warning has to be true — and this keeps it true.
        given:
        def wrongCase = '''SELECT p."ProductName",
                                  ROUND(SUM(d."UnitPrice" * d."Quantity"
                                    * (1 - d."Discount")), 2) AS "Total sales"
                           FROM "Products" p
                           JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                           GROUP BY p."ProductName"
                           ORDER BY "Total Sales" DESC LIMIT 6'''

        expect:
        if (engine == "duckdb") {
            assert sqlFor(engine).rows(wrongCase).size() == 6
        } else {
            try {
                sqlFor(engine).rows(wrongCase)
                assert false, "PostgreSQL must refuse \"Total Sales\" when the column is \"Total sales\""
            } catch (Exception expected) {
                assert expected.message?.contains('"Total Sales" does not exist')
            }
        }

        where:
        engine << ENGINES
    }

    // --- 8. THE KOAN FILE ITSELF, RUN AS WRITTEN --------------------------------------------
    //
    // Ported from episode 45, which found the hole: every feature in §7 RE-TYPES a koan's query
    // with its own aliases, so it can prove an answer right while the koans file quietly asks a
    // different question. This reads YourFirstRealReportKoans.groovy off disk, takes each koan's
    // own SQL and its own declared result — a list of rows, or the single value of
    // `shouldReturn 20, '''…'''` — puts the intended answer where the `___` is, runs it on BOTH
    // engines the way KoanBase does, and compares. Change a koan and it must stay true, or this
    // goes red.
    //
    // THE ANSWER TABLE IS THE ONLY THING WRITTEN DOWN TWICE, and it has to be: the blank is by
    // definition not in the file. Koans 9 and 10 blank the whole query, so their entry IS the
    // whole query. Keep it in koan order; the feature after this one checks the file against it.

    @Unroll
    def "[#engine] koan file, as written: '#title' runs and returns exactly what it claims"() {
        given: "the koan's own text, with the intended answer where the learner's blank is"
        def sql = koanSql(title)
        def expected = koanExpected(title)

        expect:
        resultOf(engine, sql, expected) == expected

        where:
        [engine, title] << [ENGINES, ANSWERS.keySet() as List].combinations()
    }

    def "the koans file is the one the video's koans slide describes: ten koans, its three names, two whole queries last"() {
        // The video cannot change, and its koans slide shows this file's path, ticks off "count
        // the rows before you group", "follow the pointer one more time" and "count the THINGS,
        // not the rows", and promises "…and seven more, ending with two you write yourself".
        given:
        def titles = (koansSource() =~ /(?m)^    def "(.+?)"\(\)/).collect { it[1] }

        expect: "every koan in the file has an entry in the answer table, in the same order"
        titles == (ANSWERS.keySet() as List)

        and: "ten of them: the three on the slide and seven more"
        titles.size() == 10

        and: "the slide's three names are koans 1, 2 and 4"
        titles[0] == "count the rows before you group"
        titles[1].startsWith("follow the pointer one more time")
        titles[3] == "count the THINGS, not the rows"

        and: "and the last two are written from scratch: their SQL is nothing but the blank"
        koanQueries(titles[8])*.trim() == ["___"]
        koanQueries(titles[9])*.trim() == ["___"]
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/50-select-sum-join-group-by-order-by-limit-project/scripts/${name}.sql").text
    }

    /** The koans file, read the same way script() reads the lesson's SQL. */
    private static String koansSource() {
        new File("src/koans/groovy/datazeus/learnsql/series1/_50/YourFirstRealReportKoans.groovy").text
    }

    /** One koan's source, from `def "title"()` to the next koan. */
    private static String koanBody(String title) {
        def src = koansSource()
        int at = src.indexOf('def "' + title + '"()')
        assert at >= 0: "no koan titled '${title}' in the koans file — it was renamed or removed"
        int next = src.indexOf('\n    def "', at + 1)
        next < 0 ? src.substring(at) : src.substring(at, next)
    }

    /** Every ''' … ''' SQL literal inside one koan. */
    private static List<String> koanQueries(String title) {
        (koanBody(title) =~ /(?s)'''(.*?)'''/).collect { it[1] }
    }

    /** THE INTENDED ANSWER FOR EACH BLANK, in koan order. Every koan here has exactly one blank. */
    private static final Map<String, List<String>> ANSWERS = [
            "count the rows before you group"                                    : ["count"],
            "follow the pointer one more time, and watch the row count move"     : ['"Order Details"'],
            "the money: an aggregate runs once per group"                        : ["SUM"],
            "count the THINGS, not the rows"                                     : ["DISTINCT"],
            "check the count a second way, without the joins"                    : ['"ProductID"'],
            "an average: divide the money by the products, not the order lines"  : ['count(DISTINCT p."ProductID")'],
            "rank by the money, and keep the top six"                            : ['ORDER BY "Total sales" DESC LIMIT 6'],
            "know what the cut left out"                                         : ["OFFSET"],
            "write the whole query: the supplier sales summary"                  : ['''
                SELECT s."CompanyName",
                       count(DISTINCT p."ProductID") AS "Products",
                       ROUND(SUM(d."UnitPrice" * d."Quantity"
                         * (1 - d."Discount")), 2) AS "Total sales"
                FROM "Suppliers" s
                JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                GROUP BY s."CompanyName"
                ORDER BY "Total sales" DESC
            '''],
            "write the whole query: the top three suppliers, orders and all"     : ['''
                SELECT s."CompanyName",
                       count(DISTINCT p."ProductID") AS "Products",
                       count(DISTINCT d."OrderID") AS "Orders",
                       ROUND(SUM(d."UnitPrice" * d."Quantity"
                         * (1 - d."Discount")), 2) AS "Total sales"
                FROM "Suppliers" s
                JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                GROUP BY s."CompanyName"
                ORDER BY "Total sales" DESC
                LIMIT 3
            '''],
    ]

    /** One koan's SQL with its blank filled in, ready to run. */
    private static String koanSql(String title) {
        def qs = koanQueries(title)
        assert qs.size() == 1: "koan '${title}' has ${qs.size()} queries; this helper expects one"
        def sql = qs[0]
        def answers = ANSWERS[title]
        int found = sql.count("___")
        assert found == answers.size():
                "koan '${title}' has ${found} blank(s) but the answer table has ${answers.size()}"
        answers.each { a -> sql = sql.replaceFirst(/___/, java.util.regex.Matcher.quoteReplacement(a)) }
        sql
    }

    /**
     * What the koan file itself declares, parsed out of its own shouldReturn call: a List of rows
     * for `shouldReturn([...], '''…''')`, or ONE value for `shouldReturn 20, '''…'''` — the same
     * two shapes KoanBase.shouldReturn accepts. Numbers go through the same normaliser as the live
     * results, or 2862.30 would not equal 2862.3.
     */
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

    /** Run a koan's query the way KoanBase does: every row when it expects rows, otherwise the
     *  first cell of the first row. */
    private Object resultOf(String engine, String sql, Object expected) {
        if (expected instanceof List) {
            return sqlFor(engine).rows(sql).collect { r -> (0..<r.size()).collect { i -> plain(r.getAt(i)) } }
        }
        def row = sqlFor(engine).firstRow(sql)
        row == null ? null : plain(row.getAt(0))
    }

    /** ONE SHAPE FOR BOTH ENGINES: every number becomes a scale-stripped BigDecimal, so Long vs
     *  Integer, DOUBLE vs numeric and 5.38 vs 5.3800 stop mattering before anything is compared. */
    private static Object plain(Object v) {
        v == null ? null : (v instanceof Number ? dec(v) : v)
    }

    /** How many columns a table has, as the koans' schema block counts them. The table name is a
     *  VALUE in information_schema, so a bind parameter is right here. */
    private int columns(String engine, String table) {
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM information_schema.columns
                                   WHERE table_name = ?
                                     AND table_schema NOT IN ('pg_catalog', 'information_schema')''',
                [table]).n as int
    }

    /**
     * True when a value is EXACTLY half a cent before rounding — x.xx5 with nothing after it.
     * That is where a DuckDB DOUBLE and a PostgreSQL numeric can round to different cents, so it
     * is the one value a koan must not expect. Read through toString, which for a DOUBLE is the
     * shortest decimal that round-trips: 3507.0074999999997 is correctly NOT half a cent.
     */
    private static boolean halfCent(Object v) {
        new BigDecimal(v.toString()).movePointRight(2).remainder(BigDecimal.ONE).abs()
                .compareTo(new BigDecimal("0.5")) == 0
    }

    /** Money is DECIMAL on DuckDB and numeric on PostgreSQL, and the two hand back different
     *  Java types with different scales. Compare by VALUE, never by toString or by ==, or
     *  4567.8 and 4567.80 stop being equal for reasons that have nothing to teach. */
    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }
}
