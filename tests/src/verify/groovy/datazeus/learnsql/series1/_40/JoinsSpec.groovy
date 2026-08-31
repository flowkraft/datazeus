package datazeus.learnsql.series1._40

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _40 "JOINs: INNER, LEFT, RIGHT, FULL".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted
 * here, on BOTH engines. The lesson's ten scripts:
 *
 *    1. order-lines-raw              — the problem: an order's contents, in pure numbers
 *    2. order-lines-with-names       — INNER JOIN: the same three lines, in words
 *    3. ambiguous-column             — a bare shared column is REFUSED. ERRORS.
 *    4. orders-with-customer-names   — the hands-on: episode 15's ALFKI, finally answered
 *    5. june-by-rep-inner            — THE TRAP: three people asked for, two people back
 *    6. june-by-rep-left             — LEFT JOIN: Andrew returns, holding manufactured NULLs
 *    7. june-by-rep-where            — THE DEEPER TRAP: the same LEFT JOIN, undone by WHERE
 *    8. june-by-rep-right            — RIGHT JOIN: identical rows, tables swapped
 *    9. june-by-rep-full             — FULL OUTER: unmatched rows from both sides, 80 of them
 *   10. reps-with-no-june-order      — the anti-join, and the one legitimate WHERE
 *   11. orders-with-courier          — an extra pair for the article: orders and couriers
 *
 * THE OPENING ACT IS A CONTRACT WITH EPISODE 35. It signs off with "what was actually IN
 * those orders is sitting in a different table entirely", so act 1 answers exactly that,
 * joining "Order Details" to "Products". Episode 15's own loose end — a backlog report full
 * of customer codes, and "who is ALFKI?" — is closed by the hands-on query instead.
 *
 * WHY THE OUTER-JOIN EXAMPLES ALL NARROW THE MATCH IN `ON`, AND WHY THAT IS NOT A DODGE.
 * This Northwind is referentially PERFECT — the first feature below proves it, in every
 * direction. So a plain join on a foreign key cannot lose a row here and a plain LEFT JOIN
 * cannot invent a NULL; there is simply no orphan data to teach with. Every outer join in
 * this lesson therefore adds a DATE WINDOW to the ON clause, which is both the only honest
 * way to produce an unmatched row from this data and the most common real-world LEFT JOIN
 * there is ("every X, and their Y in this period, including the ones with none"). It is also
 * exactly the shape the ON-versus-WHERE lesson needs. If a future dataset gains an orphan
 * row, the integrity feature below goes red first, and that is the intended order.
 *
 * NOTHING HERE IS ASSERTED AS AN UNORDERED ROW ORDER. Every result the lesson shows carries
 * its own ORDER BY (by "FirstName", then "OrderDate"), so pinning the rows asserts something
 * the queries actually promise. The one place engines could have differed — where a NULL
 * sorts — is checked explicitly rather than assumed: see the NULL-placement feature.
 *
 * Convention: the spec runs the SAME *.sql files the lesson and the video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 *
 * AND THEN THE KOANS, ALL TEN, in their own section at the bottom. They deliberately do NOT
 * reuse the lesson's queries — the lesson joins orders to customers and employees to orders,
 * the koans join products to categories and suppliers to their out-of-stock lines — so none
 * of the assertions above touches the data they stand on. Every koan is checked in its solved
 * form, on both engines, plus the factual claims their HINTS make: the four different row
 * counts koan 4 rests on, what koan 6 returns if you write WHERE instead of AND, and that
 * exactly one category holds two discontinued lines (koan 9).
 */
class JoinsSpec extends NorthwindGateSpec {

    // --- 0. The dataset, and the fact the whole lesson design rests on ---------------------

    def "the dataset is the small Northwind the lesson quotes"() {
        // Every "three people", "seventy-nine orders", "six suppliers" and "eight categories"
        // in the article and on the slides resolves to these numbers. This is the SMALL
        // Northwind, not the 91-customer original, so looking an answer up elsewhere gives a
        // different one.
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Employees"').n == 3 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers"').n == 25 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Categories"').n == 8 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Suppliers"').n == 6
        }
    }

    @Unroll
    def "[#engine] EVERY foreign key in this database is satisfied — no orphan rows anywhere"() {
        // THE LOAD-BEARING FACT OF THE WHOLE EPISODE DESIGN, and it is asserted rather than
        // assumed because it is the reason the lesson is shaped the way it is. If any of
        // these stops being zero, a plain LEFT JOIN starts producing NULLs on its own, the
        // video's explanation ("the only unmatched rows here are the ones YOU created with a
        // second ON condition") becomes false, and the article's note is wrong too.
        //
        // Checked in BOTH directions for the pairs the lesson and the koans use.
        expect:
        [
            'SELECT count(*) FROM "Orders" o WHERE o."CustomerID" IS NULL',
            'SELECT count(*) FROM "Orders" o WHERE NOT EXISTS (SELECT 1 FROM "Customers" c WHERE c."CustomerID" = o."CustomerID")',
            'SELECT count(*) FROM "Orders" o WHERE o."EmployeeID" IS NULL',
            'SELECT count(*) FROM "Orders" o WHERE NOT EXISTS (SELECT 1 FROM "Employees" e WHERE e."EmployeeID" = o."EmployeeID")',
            'SELECT count(*) FROM "Employees" e WHERE NOT EXISTS (SELECT 1 FROM "Orders" o WHERE o."EmployeeID" = e."EmployeeID")',
            'SELECT count(*) FROM "Customers" c WHERE NOT EXISTS (SELECT 1 FROM "Orders" o WHERE o."CustomerID" = c."CustomerID")',
            'SELECT count(*) FROM "Products" p WHERE NOT EXISTS (SELECT 1 FROM "Categories" c WHERE c."CategoryID" = p."CategoryID")',
            'SELECT count(*) FROM "Products" p WHERE NOT EXISTS (SELECT 1 FROM "Suppliers" s WHERE s."SupplierID" = p."SupplierID")',
            'SELECT count(*) FROM "Categories" c WHERE NOT EXISTS (SELECT 1 FROM "Products" p WHERE p."CategoryID" = c."CategoryID")',
            'SELECT count(*) FROM "Suppliers" s WHERE NOT EXISTS (SELECT 1 FROM "Products" p WHERE p."SupplierID" = s."SupplierID")',
        ].every { sqlFor(engine).firstRow(it).values().first() == 0 }

        where:
        engine << ENGINES
    }

    // --- 1. The problem: a table made entirely of numbers ---------------------------------

    @Unroll
    def "[#engine] order 3 has three lines, and every cell of them is a number"() {
        given:
        def rows = sqlFor(engine).rows(script("order-lines-raw"))

        expect: "three things were bought on order 3, and they are named only by a pointer"
        rows.size() == 3
        rows*.ProductID == [1, 2, 4]
        rows*.Quantity == [8, 5, 3]

        and: "THE POINT OF THE SLIDE: there is no product name in this table at all"
        !columnsOf(engine, "Order Details").contains("ProductName")
        columnsOf(engine, "Order Details").toSet() ==
                ["OrderID", "ProductID", "UnitPrice", "Quantity", "Discount"].toSet()

        and: "order 3 belongs to Berglunds, which is what the slide calls it"
        sqlFor(engine).firstRow('''SELECT c."CompanyName" AS n FROM "Orders" o
                                   JOIN "Customers" c ON c."CustomerID" = o."CustomerID"
                                   WHERE o."OrderID" = 3''').n == "Berglunds snabbköp"

        where:
        engine << ENGINES
    }

    // --- 2. INNER JOIN: the same rows, in words -------------------------------------------

    @Unroll
    def "[#engine] a JOIN turns the pointers into product names"() {
        given:
        def joined = sqlFor(engine).rows(script("order-lines-with-names"))

        and: "and the raw lines, to compare against"
        def raw = sqlFor(engine).rows(script("order-lines-raw"))

        expect: "THE SAME THREE LINES — a join added a column, it did not change which rows"
        joined.size() == raw.size()

        and: "with the names and quantities the video puts on screen"
        joined.collect { [it.ProductName, it.Quantity] } ==
                [["Chai", 8], ["Chang", 5], ["Chef Antons Cajun Seasoning", 3]]

        and: "and the prices are the ones on the ORDER LINE, which is what d.\"UnitPrice\" means"
        joined.collect { dec(it.UnitPrice) } == [dec("18.0000"), dec("19.0000"), dec("22.0000")]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] joining every order line to its product neither loses nor multiplies rows"() {
        // The claim behind "a join added a column, it did not change which rows": ProductID is
        // unique in "Products" and every order line has a real one, so this join is one-to-one
        // from the line's point of view. Worth pinning, because the moment it stops being true
        // the opening slides silently gain or lose rows.
        expect:
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Order Details" d
                                   JOIN "Products" p ON p."ProductID" = d."ProductID"''').n == 193

        and: "and 193 is simply how many order lines there are"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Order Details"').n == 193

        where:
        engine << ENGINES
    }

    // --- 3. The ambiguous column -----------------------------------------------------------

    @Unroll
    def "[#engine] a bare shared column across two tables is REFUSED, and the message says ambiguous"() {
        // The video draws this failing, so it has to keep failing. The two engines word it
        // differently — "Binder Error: Ambiguous reference to column name" on DuckDB against
        // SQLSTATE 42702 on PostgreSQL — so what is asserted on both is the part that is the
        // lesson: it throws, and the database itself says the column is ambiguous rather than
        // guessing which table was meant.
        when:
        sqlFor(engine).rows(script("ambiguous-column"))

        then:
        def e = thrown(Exception)
        e.message.toLowerCase().contains("ambiguous")
        e.message.contains("UnitPrice")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] UnitPrice really is on BOTH tables, which is why that query cannot be answered"() {
        // The slide's claim, and the reason the error is a good teaching moment rather than a
        // technicality: the two columns MEAN different things — what the line sold for against
        // what the catalogue charges now — so "which one did you mean" is a real question.
        expect:
        columnsOf(engine, "Order Details").contains("UnitPrice")
        columnsOf(engine, "Products").contains("UnitPrice")

        and: "and qualifying it either way is accepted, which is the fix the slide shows"
        sqlFor(engine).rows('''SELECT d."UnitPrice" FROM "Order Details" d
                               JOIN "Products" p ON p."ProductID" = d."ProductID"
                               WHERE d."OrderID" = 3''').size() == 3
        sqlFor(engine).rows('''SELECT p."UnitPrice" FROM "Order Details" d
                               JOIN "Products" p ON p."ProductID" = d."ProductID"
                               WHERE d."OrderID" = 3''').size() == 3

        where:
        engine << ENGINES
    }

    def "the PostgreSQL error the video's panel draws is the real one, word for word"() {
        // NOT @Unroll'd across ENGINES on purpose: the learner types into CloudBeaver, which
        // is PostgreSQL, so the panel shows PostgreSQL's wording — SQLSTATE 42702 and the
        // message below. Read off postgres:16.2 on 2026-08-30. If a future PostgreSQL rewords
        // it, this fails and the slide gets updated rather than quietly lying.
        when:
        sqlFor("postgres").rows(script("ambiguous-column"))

        then:
        def e = thrown(Exception)
        e.message.contains('column reference "UnitPrice" is ambiguous')
    }

    // --- 3b. The hands-on query: the ALFKI thread from episode 15, finally closed ----------

    @Unroll
    def "[#engine] the hands-on query answers who placed the five most recent orders"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-with-customer-names"))

        expect: "the same five orders episode 15 left as a column of codes"
        rows*.OrderID == [7, 5, 6, 4, 79]

        and: "now carrying names — including ALFKI, the code that started all this"
        rows*.CompanyName == ["Berglunds snabbköp", "Ana Trujillo Emparedados y helados",
                              "Around the Horn", "Alfreds Futterkiste", "Ottilies Käseladen"]
        rows[3].CompanyName == "Alfreds Futterkiste"

        and: "and ALFKI really is that customer's code"
        sqlFor(engine).firstRow('''SELECT "CompanyName" AS n FROM "Customers"
                                   WHERE "CustomerID" = 'ALFKI'''').n == "Alfreds Futterkiste"

        where:
        engine << ENGINES
    }

    // --- 4. THE TRAP: an INNER JOIN drops a person -----------------------------------------

    @Unroll
    def "[#engine] the June review asked for three people and comes back with two"() {
        given:
        def rows = sqlFor(engine).rows(script("june-by-rep-inner"))

        expect: "four real orders, and every one of them is real"
        rows*.FirstName == ["Janet", "Janet", "Nancy", "Nancy"]
        rows*.OrderID == [4, 7, 6, 5]

        and: "THE BUG: there are three employees and this report names two"
        rows*.FirstName.toSet().size() == 2
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Employees"').n == 3

        and: "Andrew is the one who is missing, and he is missing because he matched nothing"
        !("Andrew" in rows*.FirstName)
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "EmployeeID" = 2
                                     AND "OrderDate" >= DATE '2024-06-01' ''').n == 0

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] Andrew Fuller is employee 2, and he is the Vice President — which is why the blank row matters"() {
        // The video is careful NOT to imply Andrew was idle: he is the VP of Sales and a month
        // with no orders of his own is entirely normal. The teaching point is that the blank
        // row is INFORMATION and the INNER JOIN threw it away. Both halves of that are pinned
        // here so the slide's wording stays true to the data.
        given:
        def andrew = sqlFor(engine).firstRow('SELECT * FROM "Employees" WHERE "EmployeeID" = 2')

        expect:
        andrew.FirstName == "Andrew"
        andrew.LastName == "Fuller"
        (andrew.Title as String).contains("Vice President")

        and: "and he HAS taken orders — just not in June, which is the whole point"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "EmployeeID" = 2').n > 0

        where:
        engine << ENGINES
    }

    // --- 5. LEFT JOIN, and the NULLs this lesson manufactures ------------------------------

    @Unroll
    def "[#engine] LEFT JOIN brings Andrew back, holding two cells the database invented"() {
        given:
        def rows = sqlFor(engine).rows(script("june-by-rep-left"))

        expect: "five rows now, and all three people are named"
        rows.size() == 5
        rows*.FirstName == ["Andrew", "Janet", "Janet", "Nancy", "Nancy"]

        and: "the four June orders are unchanged"
        rows.drop(1)*.OrderID == [4, 7, 6, 5]

        and: "THE MANUFACTURED NULLs — the hand-off to Series 1 · 45. Andrew's order number"
        and: "and order date are NULL, and no such order exists anywhere in the database"
        rows[0].FirstName == "Andrew"
        rows[0].OrderID == null
        rows[0].OrderDate == null

        and: "one row per match: Janet matched twice and appears twice"
        rows.count { it.FirstName == "Janet" } == 2
        rows.count { it.FirstName == "Nancy" } == 2
        rows.count { it.FirstName == "Andrew" } == 1

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the LEFT JOIN keeps every employee and adds exactly one row per extra match"() {
        // "Three people in, five rows out, and both numbers are correct" — the fan-out beat.
        // Stated as arithmetic so it cannot drift: rows = matches + employees with no match.
        given:
        def matches = sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                                 WHERE "OrderDate" >= DATE '2024-06-01' ''').n
        def unmatched = sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Employees" e
                                                   WHERE NOT EXISTS (SELECT 1 FROM "Orders" o
                                                     WHERE o."EmployeeID" = e."EmployeeID"
                                                       AND o."OrderDate" >= DATE '2024-06-01')''').n

        expect:
        matches == 4
        unmatched == 1
        sqlFor(engine).rows(script("june-by-rep-left")).size() == matches + unmatched

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] Andrew sorts FIRST on both engines, so the video's row order is not luck"() {
        // The slide puts the blank row at the TOP, where it cannot be missed. That holds
        // because "FirstName" is never NULL and Andrew is alphabetically first — NOT because
        // of where either engine files a NULL. Pinned so a future re-ordering of the slide
        // has to come back through here.
        expect:
        sqlFor(engine).rows(script("june-by-rep-left"))[0].FirstName == "Andrew"

        and: "no employee has an empty first name, which is what makes that sort deterministic"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Employees" WHERE "FirstName" IS NULL').n == 0

        where:
        engine << ENGINES
    }

    // --- 6. THE DEEPER TRAP: a WHERE on the right-hand table undoes the LEFT JOIN ----------

    @Unroll
    def "[#engine] moving the date from ON to WHERE turns the LEFT JOIN back into an INNER JOIN"() {
        given: "the same LEFT JOIN, with the date condition moved down into a WHERE"
        def viaWhere = sqlFor(engine).rows(script("june-by-rep-where"))

        and: "the INNER JOIN from before, and the LEFT JOIN that fixed it"
        def viaInner = sqlFor(engine).rows(script("june-by-rep-inner"))
        def viaLeft = sqlFor(engine).rows(script("june-by-rep-left"))

        expect: "THE WHOLE POINT: it is now identical to the INNER JOIN, word for word"
        viaWhere*.FirstName == viaInner*.FirstName
        viaWhere*.OrderID == viaInner*.OrderID

        and: "and Andrew — the row the LEFT JOIN went to the trouble of keeping — is gone again"
        !("Andrew" in viaWhere*.FirstName)
        "Andrew" in viaLeft*.FirstName
        viaWhere.size() == viaLeft.size() - 1

        and: "the query still SAYS left join, which is what makes this so hard to spot"
        script("june-by-rep-where").toUpperCase().contains("LEFT JOIN")

        where:
        engine << ENGINES
    }

    // --- 7. RIGHT JOIN: the same query, mirrored -------------------------------------------

    @Unroll
    def "[#engine] RIGHT JOIN with the tables swapped returns exactly what LEFT JOIN returned"() {
        // The claim the slide makes, and the reason it needs no result card of its own.
        expect:
        sqlFor(engine).rows(script("june-by-rep-right"))*.FirstName ==
                sqlFor(engine).rows(script("june-by-rep-left"))*.FirstName

        and:
        sqlFor(engine).rows(script("june-by-rep-right"))*.OrderID ==
                sqlFor(engine).rows(script("june-by-rep-left"))*.OrderID

        where:
        engine << ENGINES
    }

    // --- 8. FULL OUTER: both sides, and the number that makes the point --------------------

    @Unroll
    def "[#engine] FULL OUTER keeps unmatched rows from BOTH sides — eighty of them"() {
        given:
        def rows = sqlFor(engine).rows(script("june-by-rep-full"))

        expect: "EIGHTY, the number the slide says out loud"
        rows.size() == 80

        and: "and it decomposes exactly as the video explains it: 4 matched June orders,"
        and: "one employee with no June order, and the 75 orders that are not June orders"
        rows.count { it.FirstName != null && it.OrderID != null } == 4
        rows.count { it.FirstName != null && it.OrderID == null } == 1
        rows.count { it.FirstName == null && it.OrderID != null } == 75

        and: "the top of the result is what the slide draws: Andrew blank, then the four"
        rows.take(5)*.FirstName == ["Andrew", "Janet", "Janet", "Nancy", "Nancy"]

        and: "and the sixth row is the first of the right-hand orphans — order 8, from 2022"
        rows[5].FirstName == null
        rows[5].OrderID == 8

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the four join types give four different row counts on the same ON clause"() {
        // The four-way picture, as arithmetic. This is what makes "each one keeps a different
        // set of rows" a claim the gate proves rather than a slogan on a summary slide.
        expect:
        sqlFor(engine).rows(script("june-by-rep-inner")).size() == 4
        sqlFor(engine).rows(script("june-by-rep-left")).size() == 5
        sqlFor(engine).rows(script("june-by-rep-right")).size() == 5
        sqlFor(engine).rows(script("june-by-rep-full")).size() == 80

        where:
        engine << ENGINES
    }

    // --- 9. The anti-join, and the hands-on query -----------------------------------------

    @Unroll
    def "[#engine] IS NULL after a LEFT JOIN finds exactly the people who matched nothing"() {
        given:
        def rows = sqlFor(engine).rows(script("reps-with-no-june-order"))

        expect: "the one legitimate WHERE on the right-hand table, and it answers the question"
        rows.size() == 1
        rows[0].FirstName == "Andrew"
        rows[0].LastName == "Fuller"

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the hands-on query: the couriers who carried our five dearest deliveries"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-with-courier"))

        expect:
        rows*.OrderID == [72, 59, 46, 33, 20]
        rows*.CompanyName == ["United Package", "Speedy Express", "Federal Shipping",
                              "United Package", "Speedy Express"]

        and: "the freight figures episode 15 already put on record"
        rows.collect { dec(it.Freight) } ==
                [dec("98.9200"), dec("97.5300"), dec("96.1400"), dec("95.7500"), dec("94.3600")]

        where:
        engine << ENGINES
    }

    // --- 10. What the KOANS stand on -------------------------------------------------------
    //
    // THE KOANS DO NOT REUSE THE LESSON'S QUERIES. The lesson joins ORDERS to CUSTOMERS and
    // then EMPLOYEES to ORDERS; the koans join PRODUCTS to CATEGORIES and SUPPLIERS, and ask
    // which suppliers have a line we have run out of. That is the house convention — pom.xml
    // states it as "the koans are related practice, not a blanked copy of the gate" — and it
    // exists so a learner applies the idea somewhere new instead of retyping a query they
    // just watched.
    //
    // Which is exactly why the koans need their own assertions. Nothing in the sections above
    // touches "UnitsInStock", "Discontinued", "SupplierID" or "CategoryID", so a shift in that
    // data would surface as a RED KOAN ON A STUDENT'S SCREEN with a green gate behind it —
    // the worst possible place to discover it.
    //
    // Every koan is asserted with the SQL its solved form produces, on BOTH engines. The koans
    // run on DuckDB only, but each is written to give the same answer in CloudBeaver against
    // PostgreSQL; if that stops being true, a learner checking their work is told they are
    // wrong when they are right.

    @Unroll
    def "[#engine] koan 1: a product joined to its category"() {
        expect:
        sqlFor(engine).rows('''SELECT p."ProductName", c."CategoryName"
                               FROM "Products" p
                               JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
                               ORDER BY p."ProductName" LIMIT 3''')
                .collect { [it.ProductName, it.CategoryName] } ==
                [["Aniseed Syrup", "Condiments"],
                 ["Boston Crab Meat", "Seafood"],
                 ["Camembert Pierrot", "Dairy Products"]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 2: the five lines that come from Japan, and Country is on Suppliers only"() {
        expect:
        sqlFor(engine).rows('''SELECT p."ProductName" FROM "Products" p
                               JOIN "Suppliers" s ON s."SupplierID" = p."SupplierID"
                               WHERE s."Country" = 'Japan'
                               ORDER BY p."ProductName"''')*.ProductName ==
                ["Filo Mix", "Genen Shouyu", "Ikura", "Mishi Kobe Niku", "Tofu"]

        and: "THE KOAN'S HINT IS TRUE: only one of the two tables has a Country column, so a"
        and: "student who guesses the other alias gets an error rather than a wrong answer"
        columnsOf(engine, "Suppliers").contains("Country")
        !columnsOf(engine, "Products").contains("Country")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 3: a second condition in ON — the two suppliers we have to chase"() {
        expect:
        sqlFor(engine).rows('''SELECT s."CompanyName", p."ProductName"
                               FROM "Suppliers" s
                               JOIN "Products" p
                                 ON p."SupplierID" = s."SupplierID"
                                AND p."UnitsInStock" = 0
                               ORDER BY s."CompanyName"''')
                .collect { [it.CompanyName, it.ProductName] } ==
                [["Pasta Buttini s.r.l.", "Gorgonzola Telino"],
                 ["Pavlova Ltd", "Thuringer Rostbratwurst"]]

        and: "THE HINT'S FACT: exactly two lines in the catalogue are at zero stock"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "UnitsInStock" = 0').n == 2

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 4: the four join types answer 2, 6, 20 and 24 — so the blank cannot be fudged"() {
        // Koan 4 is the ONE koan checked with a scalar rather than a row set, and this is what
        // makes that safe: the four candidate keywords give four DIFFERENT counts, so only
        // LEFT answers 6. If a data change ever collapsed two of these onto the same number,
        // the koan would start passing with a wrong answer — silently. This is the guard.
        expect:
        countJoin(engine, "") == 2
        countJoin(engine, "LEFT") == 6
        countJoin(engine, "RIGHT") == 20
        countJoin(engine, "FULL") == 24

        and: "and all four are distinct, which is the property the koan actually relies on"
        [countJoin(engine, ""), countJoin(engine, "LEFT"),
         countJoin(engine, "RIGHT"), countJoin(engine, "FULL")].toSet().size() == 4

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koans 5 and 8: LEFT and RIGHT both list all six suppliers, four holding NULL"() {
        given: "koan 5 — every supplier, and the out-of-stock line they owe us, if any"
        def viaLeft = sqlFor(engine).rows('''SELECT s."CompanyName", p."ProductName"
                                             FROM "Suppliers" s
                                             LEFT JOIN "Products" p
                                               ON p."SupplierID" = s."SupplierID"
                                              AND p."UnitsInStock" = 0
                                             ORDER BY s."CompanyName"''')

        and: "koan 8 — the same question with the tables written the other way round"
        def viaRight = sqlFor(engine).rows('''SELECT s."CompanyName", p."ProductName"
                                              FROM "Products" p
                                              RIGHT JOIN "Suppliers" s
                                                ON p."SupplierID" = s."SupplierID"
                                               AND p."UnitsInStock" = 0
                                              ORDER BY s."CompanyName"''')

        expect:
        viaLeft.collect { [it.CompanyName, it.ProductName] } ==
                [["Exotic Liquids", null],
                 ["Grandma Kellys Homestead", null],
                 ["New Orleans Cajun Delights", null],
                 ["Pasta Buttini s.r.l.", "Gorgonzola Telino"],
                 ["Pavlova Ltd", "Thuringer Rostbratwurst"],
                 ["Tokyo Traders", null]]

        and: "koan 8's claim: RIGHT with the tables swapped is the same answer, same order"
        viaRight.collect { [it.CompanyName, it.ProductName] } ==
                viaLeft.collect { [it.CompanyName, it.ProductName] }

        and: "FOUR manufactured NULLs — what koan 5's comment tells the student to expect"
        viaLeft.count { it.ProductName == null } == 4

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 6: writing WHERE instead of AND really does collapse it to two rows"() {
        // The koan's comment tells the student what happens if they put WHERE in the blank:
        // "two rows, not six — the same two as koan 3". A WRONG NUMBER IN A HINT IS WORSE THAN
        // NO HINT, so the claim is checked rather than asserted in prose.
        given: "the blank filled the WRONG way, which is what the hint invites them to try"
        def wrong = sqlFor(engine).rows('''SELECT s."CompanyName", p."ProductName"
                                           FROM "Suppliers" s
                                           LEFT JOIN "Products" p
                                             ON p."SupplierID" = s."SupplierID"
                                           WHERE p."UnitsInStock" = 0
                                           ORDER BY s."CompanyName"''')

        and: "and koan 3's INNER JOIN, which the hint says it collapses to"
        def inner = sqlFor(engine).rows('''SELECT s."CompanyName", p."ProductName"
                                           FROM "Suppliers" s
                                           JOIN "Products" p
                                             ON p."SupplierID" = s."SupplierID"
                                            AND p."UnitsInStock" = 0
                                           ORDER BY s."CompanyName"''')

        expect:
        wrong.size() == 2
        wrong.collect { [it.CompanyName, it.ProductName] } ==
                inner.collect { [it.CompanyName, it.ProductName] }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 7: the anti-join names the four suppliers with nothing out of stock"() {
        expect:
        sqlFor(engine).rows('''SELECT s."CompanyName"
                               FROM "Suppliers" s
                               LEFT JOIN "Products" p
                                 ON p."SupplierID" = s."SupplierID"
                                AND p."UnitsInStock" = 0
                               WHERE p."ProductID" IS NULL
                               ORDER BY s."CompanyName"''')*.CompanyName ==
                ["Exotic Liquids", "Grandma Kellys Homestead",
                 "New Orleans Cajun Delights", "Tokyo Traders"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 9: eight categories in, NINE rows out — Meat slash Poultry appears twice"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT c."CategoryName", p."ProductName"
                                          FROM "Categories" c
                                          LEFT JOIN "Products" p
                                            ON p."CategoryID" = c."CategoryID"
                                           AND p."Discontinued"
                                          ORDER BY c."CategoryName", p."ProductName"''')

        expect:
        rows.collect { [it.CategoryName, it.ProductName] } ==
                [["Beverages", null],
                 ["Condiments", null],
                 ["Confections", null],
                 ["Dairy Products", null],
                 ["Grains/Cereals", null],
                 ["Meat/Poultry", "Mishi Kobe Niku"],
                 ["Meat/Poultry", "Thuringer Rostbratwurst"],
                 ["Produce", null],
                 ["Seafood", null]]

        and: "THE KOAN'S WHOLE POINT: eight rows in the left table, nine rows out"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Categories"').n == 8
        rows.size() == 9

        and: "and it is one category that holds two discontinued lines, exactly as the hint says"
        rows.count { it.CategoryName == "Meat/Poultry" } == 2
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "Discontinued"').n == 2

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 10: the whole query — the non-USA suppliers we still have to chase"() {
        given: "the query the student writes from scratch: LEFT JOIN, one condition in ON and"
        and: "one in WHERE, and they are NOT interchangeable"
        def rows = sqlFor(engine).rows('''SELECT s."CompanyName", p."ProductName"
                                          FROM "Suppliers" s
                                          LEFT JOIN "Products" p
                                            ON p."SupplierID" = s."SupplierID"
                                           AND p."UnitsInStock" = 0
                                          WHERE s."Country" <> 'USA'
                                          ORDER BY s."CompanyName", p."ProductName"''')

        expect:
        rows.collect { [it.CompanyName, it.ProductName] } ==
                [["Exotic Liquids", null],
                 ["Pasta Buttini s.r.l.", "Gorgonzola Telino"],
                 ["Pavlova Ltd", "Thuringer Rostbratwurst"],
                 ["Tokyo Traders", null]]

        and: "THE WHERE HAS TO MATTER — exactly two suppliers are in the USA, so the filter"
        and: "removes something and not everything"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Suppliers"
                                   WHERE "Country" = 'USA' ''').n == 2

        and: "AND THE TWO CONDITIONS ARE NOT INTERCHANGEABLE, which is the exam: putting the"
        and: "stock test in WHERE too collapses four rows to two"
        sqlFor(engine).rows('''SELECT s."CompanyName" FROM "Suppliers" s
                               LEFT JOIN "Products" p ON p."SupplierID" = s."SupplierID"
                               WHERE p."UnitsInStock" = 0 AND s."Country" <> 'USA' ''').size() == 2

        where:
        engine << ENGINES
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/40-joins/scripts/${name}.sql").text
    }

    /** Koan 4's four counts, one join keyword at a time. "" is the plain (INNER) join.
     *
     *  CONCATENATED, NOT INTERPOLATED, AND THAT IS NOT A STYLE CHOICE. Groovy's Sql treats a
     *  GString's ${...} slots as BIND PARAMETERS, so writing this as one """...${kind}..."""
     *  string sends the driver `? JOIN "Products" p` and the join keyword arrives as a
     *  parameter value. Both engines then reject it at parse time — "syntax error at or near
     *  $1" on PostgreSQL, "syntax error at or near ?" on DuckDB. A join keyword is SQL text,
     *  not data, so it has to be concatenated into a plain String before the call. */
    private int countJoin(String engine, String kind) {
        String sql = 'SELECT count(*) AS n FROM "Suppliers" s ' + kind +
                ' JOIN "Products" p ON p."SupplierID" = s."SupplierID" AND p."UnitsInStock" = 0'
        sqlFor(engine).firstRow(sql).n as int
    }

    /** Freight is DECIMAL(19,4) on DuckDB and numeric on PostgreSQL, and the two hand back
     *  different Java types with different scales. Compare by VALUE, never by toString or by
     *  ==, or 98.92 and 98.9200 stop being equal for reasons that have nothing to teach. */
    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }

    /** The column names of a table, for the two "this column is only on that table" claims. */
    private List<String> columnsOf(String engine, String table) {
        sqlFor(engine).rows("""SELECT "column_name" AS c FROM information_schema.columns
                               WHERE table_name = ?""", [table])*.c*.toString()
    }
}
