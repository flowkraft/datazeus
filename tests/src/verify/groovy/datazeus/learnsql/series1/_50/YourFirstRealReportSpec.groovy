package datazeus.learnsql.series1._50

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _50
 * "SELECT + JOIN + GROUP BY — Building Your First Real Report".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted
 * here, on BOTH engines. The lesson's ten scripts:
 *
 *    1. count-orders            — the grain you start from: 79 orders
 *    2. customers-and-orders    — one hop. Still 79 rows: the join added a column
 *    3. add-the-order-lines     — two hops. 193 rows. THE CHECK FIRES
 *    4. sales-by-customer       — the money, one number per customer
 *    5. orders-counted-wrong    — THE BUG: count(o."OrderID") counts LINES, not orders
 *    6. customer-sales-summary  — the report: count(DISTINCT ...) puts it right
 *    7. report-total            — the money total, through all three tables
 *    8. total-a-second-way      — the same total, with no join at all
 *    9. orders-a-second-way     — 79 again, from "Order Details" alone
 *   10. avg-order-value         — the column that makes the bug hurt
 *
 * ── WHY THIS EPISODE HAS THREE TABLES, AND WHERE THE LINE WITH SERIES 2 IS ──────────────
 *
 * Episode 40 taught JOIN on TWO tables and deferred "three or more" to Series 2 · 00. This
 * episode uses THREE, deliberately and with the boundary drawn:
 *
 *   WHAT 50 DOES. It is the Series 1 PROJECT, and the report it builds is the one DataPallas
 *     actually ships (see the sample assertion at the bottom). That report needs three tables
 *     because the customer's NAME, the ORDERS that are theirs and the MONEY are three hops
 *     apart. A project episode that pretended otherwise would be a fake project. It chains one
 *     more JOIN of exactly the shape 40 taught and spends its time on GRAIN and CHECKING —
 *     not on join mechanics, of which it teaches nothing new.
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
 * one. `ROUND(decimal / bigint, 2)` comes back DOUBLE on DuckDB and NUMERIC on PostgreSQL, so
 * DuckDB prints 1522.6 where CloudBeaver prints 1522.60. Same number, different rendering —
 * the same class of thing episode 07 already warns about. The article and the video print the
 * CloudBeaver form, because that is what the learner types into. Asserted by value on both.
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

        and: "the top five the video puts on screen"
        rows.take(5)*.CompanyName == ["Cactus Comidas para llevar", "Blauer See Delikatessen",
                                      "Frankenversand", "Lehmanns Marktstand", "Ernst Handel"]
        rows.take(5).collect { dec(it["Total sales"]) } ==
                [dec("4567.80"), dec("4318.80"), dec("4267.27"), dec("4077.12"), dec("3902.78")]

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

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the wrong count inflates EVERY customer, not just the worst one"() {
        // The slide names Alfreds because it is the biggest gap, but the point is that the
        // whole column is wrong. Asserted as a property rather than row by row: no customer's
        // line count is smaller than their order count, and at least one is strictly bigger —
        // in fact every customer with more than one line per order is overstated.
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
    def "[#engine] who is our number one customer has THREE different answers, and the lesson says so"() {
        // Episode 10 asked it, 15 deferred it, 30 answered it two ways and said the third
        // needed a join. This is that third way, and the whole point is that it disagrees with
        // both of the others. All three are asserted together so the claim cannot rot.
        given: "by orders placed — a three-way tie at five, which is episode 30's answer"
        def byOrders = sqlFor(engine).rows('''SELECT c."CompanyName" AS n, count(*) AS c
                                              FROM "Orders" o
                                              JOIN "Customers" c ON c."CustomerID" = o."CustomerID"
                                              GROUP BY c."CompanyName"
                                              ORDER BY c DESC, n''')

        and: "by freight paid — one customer on three orders, also episode 30"
        def byFreight = sqlFor(engine).rows('''SELECT c."CompanyName" AS n, SUM(o."Freight") AS f
                                               FROM "Orders" o
                                               JOIN "Customers" c ON c."CustomerID" = o."CustomerID"
                                               GROUP BY c."CompanyName"
                                               ORDER BY f DESC''')

        and: "by money actually spent — THIS lesson's answer"
        def byMoney = sqlFor(engine).rows(script("customer-sales-summary"))

        expect: "three at the top on orders placed, and they are the ones 30 named"
        byOrders.take(3)*.n == ["Alfreds Futterkiste", "Ana Trujillo Emparedados y helados",
                                "Berglunds snabbköp"]
        byOrders.take(3).every { it.c == 5 }
        byOrders[3].c == 4

        and: "a different single winner on freight"
        byFreight[0].n == "Frankenversand"

        and: "and a THIRD answer on money — in neither of the other two top spots"
        byMoney[0].CompanyName == "Cactus Comidas para llevar"
        !(byMoney[0].CompanyName in byOrders.take(3)*.n)
        byMoney[0].CompanyName != byFreight[0].n

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

    @Unroll
    def "[#engine] adding up the ROUNDED column gives 58153.34, and the three pence are real"() {
        // FOUND BY THE GATE, 2026-08-30, and kept because a reader WILL hit it. The report
        // rounds each customer's total to the penny, and twenty-five roundings do not cancel:
        // the column adds up to 58153.34 while the true total is 58153.3075, i.e. 58153.31.
        // Three pence apart, both defensible, and this is exactly the disagreement that starts
        // arguments with finance.
        //
        // IT MATTERS HERE SPECIFICALLY because the Excel template DataPallas ships
        // (customer-sales-template-excel.html) computes its footer by summing the column it
        // just printed — so the generated spreadsheet says 58153.34 and a SELECT against the
        // database says 58153.31.
        //
        // THE RULE THE ARTICLE STATES: round once, at the end. A column of rounded numbers is
        // not obliged to add up to the rounded total, and neither figure is a bug.
        // The VIDEO deliberately does not cover this — its beat is "two routes, one number",
        // and a third route with a different answer would blunt it. The article carries it,
        // because a reader who adds the column up has to be told why, or the lesson looks
        // wrong to them.
        given:
        def sumOfRoundedRows = sqlFor(engine).rows(script("customer-sales-summary"))
                .collect { dec(it["Total sales"]) }.sum()

        expect:
        dec(sumOfRoundedRows) == dec("58153.34")

        and: "and the unrounded truth it drifts from"
        dec(sqlFor(engine).firstRow('''SELECT SUM(d."UnitPrice" * d."Quantity"
                                         * (1 - d."Discount")) AS n
                                       FROM "Order Details" d''').n) == dec("58153.3075")

        and: "three pence, and the drift is upward — which is what makes it look like a rounding"
        and: "error rather than a lost row"
        dec(sumOfRoundedRows) - dec("58153.31") == dec("0.03")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the order count survives the second check too: 79, from the lines alone"() {
        expect:
        sqlFor(engine).firstRow(script("orders-a-second-way")).values().first() == 79

        and: "which is the same 79 the report's own Orders column adds up to"
        sqlFor(engine).rows(script("customer-sales-summary"))*.Orders*.intValue().sum() == 79

        and: "and the WRONG version does not survive it — 193, not 79. THAT is the check"
        and: "catching the bug rather than the bug being spotted by eye."
        sqlFor(engine).rows(script("orders-counted-wrong"))*.Orders*.intValue().sum() == 193

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

    // --- 7. The report DataPallas actually ships --------------------------------------------

    @Unroll
    def "[#engine] the shipped Customer Sales Summary sample agrees with the lesson, row for row"() {
        // THE LESSON'S CLAIM THAT THIS IS A REAL REPORT, MADE CHECKABLE. The query below is a
        // COPY of the one DataPallas ships as sample 13, GENERATE-CUSTOMER-SALES-SUMMARY-SQL2XLSX
        // — read off asbl/.../NoExeAssembler.java on 2026-08-30, where it is assembled into
        // config/samples/g-sql2xls-cst-sles/reporting.xml. It renders through
        // samples/reports/northwind/customer-sales-template-excel.html into
        // CustomerSalesSummary.xlsx.
        //
        // TWO DIFFERENCES FROM THE TEACHING QUERY, AND BOTH ARE PRODUCTION DEFENCES the lesson
        // names rather than teaches: COALESCE("Discount", 0) survives a nullable discount
        // column (this Northwind has none), and the CASE guard survives a customer with zero
        // orders (an INNER JOIN cannot produce one). The learner can read both by the end of
        // the episode, which is the point of showing it.
        //
        // IF THIS EVER GOES RED because the sample changed, the lesson's "this is the report
        // DataPallas ships" line is what needs revisiting — not this assertion.
        given:
        def shipped = sqlFor(engine).rows('''
            SELECT
              C."CustomerID",
              C."CompanyName",
              COUNT(DISTINCT O."OrderID")                        AS OrdersCount,
              SUM(OD."UnitPrice" * OD."Quantity" * (1 - COALESCE(OD."Discount",0))) AS TotalSales
            FROM "Customers" C
            JOIN "Orders" O            ON C."CustomerID" = O."CustomerID"
            JOIN "Order Details" OD    ON O."OrderID"    = OD."OrderID"
            GROUP BY C."CustomerID", C."CompanyName"
            ORDER BY TotalSales DESC
            LIMIT 20''')

        and: "the lesson's own report"
        def ours = sqlFor(engine).rows(script("customer-sales-summary"))

        expect: "same customers, same order, same counts"
        shipped*.CompanyName == ours.take(20)*.CompanyName
        shipped*.OrdersCount*.intValue() == ours.take(20)*.Orders*.intValue()

        and: "and the same money — ROUNDED HERE AND NOT IN THE SHIPPED SQL, which is the third"
        and: "difference and the one worth knowing: the sample keeps full precision in the query"
        and: "(4567.7975) and formats to the penny in the FreeMarker template, with"
        and: "?string[\"#,##0.00\"]. Our teaching query rounds in the SQL instead. Both are"
        and: "legitimate; rounding at the edge is arguably the better habit, because every"
        and: "consumer then gets to choose. Found by this gate on 2026-08-30."
        shipped.collect { money2(it.TotalSales) } ==
                ours.take(20).collect { money2(it["Total sales"]) }

        and: "ALFKI really is Alfreds Futterkiste, which is the code episode 15 left hanging"
        shipped.find { it.CustomerID == "ALFKI" }.CompanyName == "Alfreds Futterkiste"

        where:
        engine << ENGINES
    }

    // --- 8. What the KOANS stand on ---------------------------------------------------------
    //
    // THE KOANS DO NOT REUSE THE LESSON'S QUERIES. The lesson joins CUSTOMERS to ORDERS to
    // ORDER DETAILS and asks who spends the most; the koans join SUPPLIERS to PRODUCTS to
    // ORDER DETAILS and ask whose goods earn the most. That is the house convention — pom.xml
    // states it as "the koans are related practice, not a blanked copy of the gate" — and it
    // exists so a learner applies the idea somewhere new instead of retyping a query they just
    // watched.
    //
    // Which is exactly why the koans need their own assertions. Nothing in the sections above
    // touches "SupplierID" or "Discontinued", so a shift in that data would surface as a RED
    // KOAN ON A STUDENT'S SCREEN with a green gate behind it — the worst possible place to
    // discover it. Every koan is asserted with the SQL its solved form produces, on BOTH
    // engines, plus every factual claim their comments make.

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

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koans 3 and 6: what each supplier earned us, alphabetically then ranked"() {
        given:
        def alpha = sqlFor(engine).rows(supplierSales('ORDER BY s."CompanyName"'))
        def ranked = sqlFor(engine).rows(supplierSales('ORDER BY "Total sales" DESC'))

        expect: "koan 3 — six rows, alphabetical"
        alpha.collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Exotic Liquids", dec("4465.75")],
                 ["Grandma Kellys Homestead", dec("5522.57")],
                 ["New Orleans Cajun Delights", dec("2862.30")],
                 ["Pasta Buttini s.r.l.", dec("9248.50")],
                 ["Pavlova Ltd", dec("18519.15")],
                 ["Tokyo Traders", dec("17535.04")]]

        and: "koan 6 — the same six, biggest earner first"
        ranked*.CompanyName == ["Pavlova Ltd", "Tokyo Traders", "Pasta Buttini s.r.l.",
                                "Grandma Kellys Homestead", "Exotic Liquids",
                                "New Orleans Cajun Delights"]

        and: "KOAN 3'S CHECKABLE FACT: the six totals add up to every penny on every line"
        dec(alpha.collect { dec(it["Total sales"]) }.sum()) == dec("58153.31")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 4: DISTINCT counts products, a bare count counts order lines"() {
        given:
        def things = sqlFor(engine).rows('''SELECT s."CompanyName" AS n,
                                                   count(DISTINCT p."ProductID") AS c
                                            FROM "Suppliers" s
                                            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                                            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                            GROUP BY s."CompanyName" ORDER BY s."CompanyName"''')

        and: "the same query with the blank filled the WRONG way — a plain count"
        def rowsNotThings = sqlFor(engine).rows('''SELECT s."CompanyName" AS n, count(*) AS c
                                                   FROM "Suppliers" s
                                                   JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                                                   JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                                   GROUP BY s."CompanyName" ORDER BY s."CompanyName"''')

        expect:
        things.collect { [it.n, it.c as int] } ==
                [["Exotic Liquids", 3], ["Grandma Kellys Homestead", 3],
                 ["New Orleans Cajun Delights", 2], ["Pasta Buttini s.r.l.", 4],
                 ["Pavlova Ltd", 3], ["Tokyo Traders", 5]]

        and: "THE HINT'S CLAIM: none above five, so a count in the twenties or fifties is the bug"
        things.every { (it.c as int) <= 5 }

        and: "and the hint's number — Tokyo Traders would come back as 51"
        rowsNotThings.find { it.n == "Tokyo Traders" }.c == 51
        rowsNotThings*.c*.intValue().sum() == 193

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 5: the same total from Order Details alone"() {
        expect:
        dec(sqlFor(engine).firstRow('''SELECT ROUND(SUM("UnitPrice" * "Quantity"
                                         * (1 - "Discount")), 2) AS n
                                       FROM "Order Details"''').n) == dec("58153.31")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 7: HAVING over five thousand keeps four of the six"() {
        given:
        def rows = sqlFor(engine).rows(supplierSales(
                '''HAVING SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) > 5000
                   ORDER BY "Total sales" DESC'''))

        expect:
        rows.collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Pavlova Ltd", dec("18519.15")],
                 ["Tokyo Traders", dec("17535.04")],
                 ["Pasta Buttini s.r.l.", dec("9248.50")],
                 ["Grandma Kellys Homestead", dec("5522.57")]]

        and: "THE HINT'S FACT: four of six, so the threshold removes something and not everything"
        rows.size() == 4

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koans 8 and 10: dropping the discontinued lines sends Pavlova from first to last"() {
        given:
        def live = sqlFor(engine).rows(supplierSales(
                'ORDER BY "Total sales" DESC', 'WHERE NOT p."Discontinued"'))

        expect: "koan 8 — the same six suppliers, a completely different ranking"
        live.collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Tokyo Traders", dec("10667.44")],
                 ["Pasta Buttini s.r.l.", dec("9248.50")],
                 ["Grandma Kellys Homestead", dec("5522.57")],
                 ["Exotic Liquids", dec("4465.75")],
                 ["New Orleans Cajun Delights", dec("2862.30")],
                 ["Pavlova Ltd", dec("2055.08")]]

        and: "THE KOAN'S CLAIM, AND IT IS THE WHOLE POINT: Pavlova Ltd was FIRST at 18519.15"
        and: "and is LAST at 2055.08, because almost all of it was one discontinued line"
        live.last().CompanyName == "Pavlova Ltd"
        sqlFor(engine).rows(supplierSales('ORDER BY "Total sales" DESC'))[0].CompanyName == "Pavlova Ltd"

        and: "and that line is Thuringer Rostbratwurst, at 16464.07 of their 18519.15"
        dec(sqlFor(engine).firstRow('''SELECT ROUND(SUM(d."UnitPrice" * d."Quantity"
                                         * (1 - d."Discount")), 2) AS n
                                       FROM "Order Details" d
                                       JOIN "Products" p ON p."ProductID" = d."ProductID"
                                       WHERE p."ProductName" = 'Thuringer Rostbratwurst' ''').n) ==
                dec("16464.07")

        and: "koan 10 — the same report with HAVING over four thousand: four rows, no Pavlova"
        def koan10 = sqlFor(engine).rows(supplierSales(
                '''HAVING SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) > 4000
                   ORDER BY "Total sales" DESC''', 'WHERE NOT p."Discontinued"'))
        koan10.collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Tokyo Traders", dec("10667.44")],
                 ["Pasta Buttini s.r.l.", dec("9248.50")],
                 ["Grandma Kellys Homestead", dec("5522.57")],
                 ["Exotic Liquids", dec("4465.75")]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 9: the whole supplier report, product counts and all"() {
        expect:
        sqlFor(engine).rows('''SELECT s."CompanyName" AS n,
                                      count(DISTINCT p."ProductID") AS p,
                                      ROUND(SUM(d."UnitPrice" * d."Quantity"
                                        * (1 - d."Discount")), 2) AS t
                               FROM "Suppliers" s
                               JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                               JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                               GROUP BY s."CompanyName"
                               ORDER BY t DESC''')
                .collect { [it.n, it.p as int, dec(it.t)] } ==
                [["Pavlova Ltd", 3, dec("18519.15")],
                 ["Tokyo Traders", 5, dec("17535.04")],
                 ["Pasta Buttini s.r.l.", 4, dec("9248.50")],
                 ["Grandma Kellys Homestead", 3, dec("5522.57")],
                 ["Exotic Liquids", 3, dec("4465.75")],
                 ["New Orleans Cajun Delights", 2, dec("2862.30")]]

        where:
        engine << ENGINES
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/50-your-first-real-report/scripts/${name}.sql").text
    }

    /** The koans' supplier report, with a tail (ORDER BY, optionally HAVING) and an optional
     *  WHERE. Six koans stand on the same shape, so it is written once here.
     *
     *  CONCATENATED, NOT INTERPOLATED, AND THAT IS NOT A STYLE CHOICE. Groovy's Sql treats a
     *  GString's ${...} slots as BIND PARAMETERS, so a """...${tail}...""" string would send the
     *  driver `? DESC` and the clause would arrive as a parameter value — rejected at parse time
     *  by both engines. A SQL clause is text, not data, so it has to be concatenated into a
     *  plain String before the call. (JoinsSpec hit exactly this on episode 40.) */
    private static String supplierSales(String tail, String where = "") {
        'SELECT s."CompanyName", ROUND(SUM(d."UnitPrice" * d."Quantity" ' +
                ' * (1 - d."Discount")), 2) AS "Total sales" ' +
                'FROM "Suppliers" s ' +
                'JOIN "Products" p ON p."SupplierID" = s."SupplierID" ' +
                'JOIN "Order Details" d ON d."ProductID" = p."ProductID" ' +
                where + ' GROUP BY s."CompanyName" ' + tail
    }

    /** Money is DECIMAL on DuckDB and numeric on PostgreSQL, and the two hand back different
     *  Java types with different scales. Compare by VALUE, never by toString or by ==, or
     *  4567.8 and 4567.80 stop being equal for reasons that have nothing to teach. */
    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }

    /** The same value AS MONEY — two decimal places, half up. Needed where one side of a
     *  comparison is rounded by the SQL and the other is not: the DataPallas sample keeps full
     *  precision in its query and formats in the template, so 4567.7975 there has to be matched
     *  against 4567.80 here. Half-up is what both engines' round(numeric, 2) does, and every
     *  figure in this lesson rounds unambiguously, so no half-way case is being papered over. */
    private static BigDecimal money2(Object v) {
        new BigDecimal(v.toString()).setScale(2, java.math.RoundingMode.HALF_UP)
    }
}
