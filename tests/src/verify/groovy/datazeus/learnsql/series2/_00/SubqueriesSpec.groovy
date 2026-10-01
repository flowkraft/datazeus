package datazeus.learnsql.series2._00

import datazeus.support.NorthwindCoGateSpec
import spock.lang.IgnoreIf
import spock.lang.Unroll

import java.math.RoundingMode
import java.sql.SQLException

/**
 * VERIFIED spec = the PUBLISH GATE for Series 2 · lesson _00
 * "Subqueries — Using One Query's Result Inside Another".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted here,
 * on BOTH engines, plus the two error messages the video draws. The whole lesson runs on
 * NORTHWIND COMPANY S (schema northwind_co_s), like the rest of Series 2; NorthwindCoGateSpec
 * re-checksums both engines against `_dataset_info` before anything here runs. The lesson's
 * scripts, and the sections that run them:
 *
 *    avg-price, above-average-price-typed, above-average-price,
 *    at-or-below-average-price                                     §1 a value
 *    aggregate-in-where, dearer-than-beverages-error                §2 the two refusals
 *    dearer-than-every-beverage                                     §2
 *    royal-raisins-customers, royal-raisins-customers-joined        §3 a list
 *    average-sale, customers-above-the-average-line,
 *    average-customer, customers-above-the-average-customer         §4 a table, and the trap
 *    ordered-and-invoiced-2024                                      §4b the case segment
 *
 * §0 asserts the dataset facts the setup section and slide quote, §5 every number the KOANS'
 * comments state, §6 runs the koans file itself, as written, on both engines (ported from
 * Series 1 · 50 §8), and §7 pins the QUERY TEXT: every SQL block the video draws is read out of
 * the video's own index.tsx and must be a lesson script (or one of the two the video alone shows),
 * and every query in the scripts, the video and the koans is checked for portable ANSI SQL
 * (double-quoted identifiers, DATE literals, no dialect functions) on top of running on both engines.
 *
 * ── THE EARN, AS ARITHMETIC ────────────────────────────────────────────────────────────────
 * The average ORDER LINE is 595.13 and 118 of the 119 customers who ever ordered beat it; the one
 * who does not, Upland Food Hall, has only ever bought two lines. The average CUSTOMER is
 * 126193.54 and 30 beat it. Both averages, the 118 and the 30, and the one exception are asserted.
 *
 * ── ORDER, TIES, TEXT AND DECIMALS ─────────────────────────────────────────────────────────
 * Every result the lesson SHOWS carries an ORDER BY, and every row it pins is a row whose sort key
 * no other row shares (asserted). THE PLANTED PRICE TIE — "Alpine Butter" and "Heritage Gouda",
 * both 43.12, kept for Series 2 · 35 — sits inside both product listings, so the article and the
 * video show those listings with an elision ("…") that covers it: asserted, so the elided rows
 * are the only ones whose order the engines may choose. NAME SORTS ARE WHERE THE ENGINES DIFFER
 * (DuckDB bytes, PostgreSQL collation): the one name-sorted list pinned here (the Royal Raisins
 * customers) is asserted identical on both engines. Money is compared BY VALUE via dec(): DuckDB
 * hands back DECIMAL or DOUBLE, PostgreSQL numeric, and 24.01 must equal 24.0100. What each tool
 * PRINTS is the video's concern (decimalAsShownInCloudBeaver), not this gate's.
 */
class SubqueriesSpec extends NorthwindCoGateSpec {

    // --- 0. The dataset the whole lesson quotes (the setup section and slide) ------------------

    @Unroll
    def "[#engine] the data is Northwind Company, as the setup section describes it"() {
        given:
        def sql = sqlFor(engine)

        expect: "the setup check: SELECT count(*) FROM \"Orders\" -> 10000"
        sql.firstRow('SELECT count(*) AS n FROM "Orders"').n == 10000

        and: "five years of orders, 2020 to 2024"
        sql.firstRow('SELECT min("OrderDate") AS a, max("OrderDate") AS b FROM "Orders"').with {
            it.a.toString().startsWith("2020-01-01") && it.b.toString().startsWith("2024-12-31")
        }

        and: "120 customers, 29 employees, 4 couriers, 80 products, 20 suppliers, 25,233 order lines"
        sql.firstRow('SELECT count(*) AS n FROM "Customers"').n == 120
        sql.firstRow('SELECT count(*) AS n FROM "Employees"').n == 29
        sql.firstRow('SELECT count(*) AS n FROM "Shippers"').n == 4
        sql.firstRow('SELECT count(*) AS n FROM "Products"').n == 80
        sql.firstRow('SELECT count(*) AS n FROM "Suppliers"').n == 20
        sql.firstRow('SELECT count(*) AS n FROM "Order Details"').n == 25233

        and: "plus invoices — one row per invoiced order"
        sql.firstRow('SELECT count(*) AS n, count(DISTINCT "OrderID") AS o FROM "Invoices"').with { it.n == 9532 && it.o == 9532 }

        where:
        engine << ENGINES
    }

    // --- 1. A VALUE: products against the average price ----------------------------------------

    @Unroll
    def "[#engine] the average price is 24.01, and thirty products sit above it"() {
        given:
        def typed = sqlFor(engine).rows(script("above-average-price-typed"))
        def bracketed = sqlFor(engine).rows(script("above-average-price"))
        def pairs = typed.collect { [it.ProductName, dec(it.UnitPrice)] }

        expect: "warm-up-average's single figure, over 80 products"
        dec(sqlFor(engine).firstRow(script("avg-price")).values().first()) == dec("24.01")

        and: "thirty-products' card and the article: 30 rows, the top four and the last two"
        typed.size() == 30
        pairs.take(4) == [["Harvest Chicken Pie", dec("81")], ["Spiced Meatballs", dec("61.12")],
                          ["Island Sausages", dec("58.42")], ["Smoked Feta", dec("53.04")]]
        pairs.takeRight(2) == [["Coastal Crispbread", dec("24.25")], ["Classic Tonic", dec("24.07")]]

        and: "BRACKETS' CLAIM: the subquery returns exactly what the typed number did, today"
        bracketed.collect { [it.ProductName, dec(it.UnitPrice)] } == pairs

        and: "the tie (43.12) is inside the elision, and every row shown has a price no other row has"
        pairs.findAll { it[1] == dec("43.12") }*.getAt(0).toSet() == ["Alpine Butter", "Heritage Gouda"] as Set
        pairs.findIndexValues { it[1] == dec("43.12") }.every { it >= 4 && it < 28 }
        (pairs.take(4) + pairs.takeRight(2)).every { p -> pairs.count { it[1] == p[1] } == 1 }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] Classic Tonic is in by six cents — the pasted number is one price change from wrong"() {
        // six-products' third turn names the edge row: 24.07 against an average of 24.01.
        expect:
        dec(sqlFor(engine).firstRow('SELECT "UnitPrice" AS p FROM "Products" WHERE "ProductName" = \'Classic Tonic\'').p) == dec("24.07")
        and: "the next product down is below the average, so the list really ends at Classic Tonic"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Products"
                                   WHERE "UnitPrice" > (SELECT AVG("UnitPrice") FROM "Products")
                                     AND "UnitPrice" < 24.07''').n == 0

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the hands-on: 50 products at or below the average, because none sits on it"() {
        // "Thirty are above it. Predict the rest." The rest is 50 ONLY because no product costs
        // exactly the average (24.0125) — asserted, or the prediction the slide invites would be a
        // trick. Nor does any product sit between the pasted 24.01 and the real average.
        expect:
        sqlFor(engine).firstRow(script("at-or-below-average-price")).values().first() == 50
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Products"
                                   WHERE "UnitPrice" = (SELECT AVG("UnitPrice") FROM "Products")''').n == 0
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Products"
                                   WHERE "UnitPrice" > 24.01
                                     AND "UnitPrice" <= (SELECT AVG("UnitPrice") FROM "Products")''').n == 0

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

    def "PostgreSQL refuses ten prices where one value belongs, with the exact error the video draws"() {
        when:
        sqlFor("postgres").rows(script("dearer-than-beverages-error"))

        then:
        SQLException e = thrown()
        e.SQLState == "21000"
        e.message.contains("more than one row returned by a subquery used as an expression")
    }

    def "DuckDB refuses it too — it does not silently take the first row"() {
        // Older DuckDB versions DID take the first row without a word. Koan 3 depends on this
        // refusal, so it is asserted rather than assumed — re-checked on 1.4.5.0 (2026-09-21),
        // which also offers SET scalar_subquery_error_on_multiple_rows=false to bring the old
        // behaviour back. The lesson never sets it.
        when:
        sqlFor("duckdb").rows(script("dearer-than-beverages-error"))

        then:
        SQLException e = thrown()
        e.message.contains("More than one row returned by a subquery used as an expression")
    }

    @Unroll
    def "[#engine] there are ten beverages, and MAX turns their prices into one value: 14 products dearer"() {
        given:
        def rows = sqlFor(engine).rows(script("dearer-than-every-beverage"))
        def pairs = rows.collect { [it.ProductName, dec(it.UnitPrice)] }

        expect: "one-value-only: Leo's 'ten... so that's ten prices'"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "CategoryID" = 1').n == 10
        sqlFor(engine).firstRow('SELECT "CategoryName" AS n FROM "Categories" WHERE "CategoryID" = 1').n == "Beverages"

        and: "the dearest beverage is Alpine Ale, at 39.1 (the article names it)"
        dec(sqlFor(engine).firstRow('SELECT MAX("UnitPrice") AS m FROM "Products" WHERE "CategoryID" = 1').m) == dec("39.1")
        sqlFor(engine).rows('SELECT "ProductName" AS n FROM "Products" WHERE "CategoryID" = 1 AND "UnitPrice" = 39.1')*.n == ["Alpine Ale"]

        and: "pick-one-value and the article: fourteen products, the top four and the last two"
        rows.size() == 14
        pairs.take(4) == [["Harvest Chicken Pie", dec("81")], ["Spiced Meatballs", dec("61.12")],
                          ["Island Sausages", dec("58.42")], ["Smoked Feta", dec("53.04")]]
        pairs.takeRight(2) == [["Spiced Ham", dec("39.32")], ["Old Town Relish", dec("39.19")]]

        and: "the 43.12 tie is inside the elision; the rows shown are unique on price"
        pairs.findIndexValues { it[1] == dec("43.12") }.every { it >= 4 && it < 12 }
        (pairs.take(4) + pairs.takeRight(2)).every { p -> pairs.count { it[1] == p[1] } == 1 }

        where:
        engine << ENGINES
    }

    // --- 3. A LIST: who bought Royal Raisins in 2024 ----------------------------------------------

    @Unroll
    def "[#engine] IN returns 8 customers, the join returns 9 rows, and Yarrow Pantry is two of them"() {
        given:
        def viaIn = sqlFor(engine).rows(script("royal-raisins-customers"))*.CompanyName
        def viaJoin = sqlFor(engine).rows(script("royal-raisins-customers-joined"))*.CompanyName

        expect: "a-list says 'Royal Raisins, product 39, has been discontinued'"
        sqlFor(engine).firstRow('SELECT "ProductName" AS n, "Discontinued" AS d FROM "Products" WHERE "ProductID" = 39').with {
            it.n == "Royal Raisins" && it.d == true
        }

        and: "eight-not-nine's card, in order — the same on both engines (no pair the collations order differently)"
        viaIn == ["Alpine Provisions", "Golden Food Hall", "Golden Pantry", "Nordic Delicatessen",
                  "Tundra Foods", "Yarrow Pantry", "Yarrow Trading", "Zenith Food Hall"]

        and: "the-join-version's card: nine rows, Yarrow Pantry twice (rows 6 and 7), the same eight names underneath"
        viaJoin.size() == 9
        viaJoin[5] == "Yarrow Pantry" && viaJoin[6] == "Yarrow Pantry"
        viaJoin.toSet() == viaIn.toSet()
        viaJoin.unique(false) == viaIn

        and: "Leo's premise: Royal Raisins sold on nine order lines in 2024"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Order Details" d
                                   JOIN "Orders" o ON o."OrderID" = d."OrderID"
                                   WHERE d."ProductID" = 39 AND o."OrderDate" >= DATE '2024-01-01' ''').n == 9

        where:
        engine << ENGINES
    }

    // --- 4. A TABLE, AND THE TRAP ------------------------------------------------------------------

    @Unroll
    def "[#engine] the average order line is 595.13 — over Order Details alone and over the report's joins"() {
        // the-real-question computes it over "Order Details"; the report joins three tables. Every
        // line appears exactly once in both, so the two agree — asserted, because the article says so.
        expect:
        dec(sqlFor(engine).firstRow(script("average-sale")).values().first()) == dec("595.13")
        dec(sqlFor(engine).firstRow('''SELECT ROUND(AVG(d."UnitPrice" * d."Quantity" * (1 - d."Discount")), 2) AS a
                                       FROM "Customers" c
                                       JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                                       JOIN "Order Details" d ON d."OrderID" = o."OrderID"''').a) == dec("595.13")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] against the average line, 118 of the 119 customers who ordered are 'above average'"() {
        given:
        def rows = sqlFor(engine).rows(script("customers-above-the-average-line"))

        expect: "everyone-above: 118 rows"
        rows.size() == 118

        and: "the card's top three and bottom two"
        rows.take(3).collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Fjord Foods", dec("797164")], ["Yarrow Pantry", dec("786022.56")],
                 ["Quayside Traders", dec("783579.03")]]
        rows.takeRight(2).collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Willow Wholefoods", dec("1662.83")], ["Tundra Grocers", dec("1239.25")]]

        and: "the totals are all different, so the row order is the query's promise, not luck"
        rows.collect { dec(it["Total sales"]) }.toSet().size() == 118

        and: "119 customers ever ordered; the 120th, Yarrow Supermarket, never has"
        sqlFor(engine).firstRow('SELECT count(DISTINCT "CustomerID") AS n FROM "Orders"').n == 119
        sqlFor(engine).rows('''SELECT "CompanyName" AS n FROM "Customers"
                               WHERE "CustomerID" NOT IN (SELECT "CustomerID" FROM "Orders")''')*.n == ["Yarrow Supermarket"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] why 595.13 is the wrong average: the one customer below it has only ever bought two lines"() {
        // average-of-what: "a customer's total is lines added together, so it almost always beats one
        // line — the one customer below it, Upland Food Hall, bought one order of two lines, 465.75".
        given:
        def upland = sqlFor(engine).firstRow('''SELECT c."CompanyName" AS n, count(DISTINCT o."OrderID") AS orders, count(*) AS lines,
                                                       ROUND(SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")), 2) AS total
                                                FROM "Customers" c
                                                JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                                                JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                                                GROUP BY c."CompanyName"
                                                ORDER BY total
                                                LIMIT 1''')
        def shown = sqlFor(engine).rows(script("customers-above-the-average-line"))*.CompanyName

        expect:
        upland.n == "Upland Food Hall"
        upland.orders == 1
        upland.lines == 2
        dec(upland.total) == dec("465.75")
        !shown.contains("Upland Food Hall")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the average customer is 126193.54, over two hundred times the average line, and 30 customers beat it"() {
        given:
        def thirty = sqlFor(engine).rows(script("customers-above-the-average-customer"))
        def all = sqlFor(engine).rows(script("customers-above-the-average-line"))

        expect: "a-table's figure"
        dec(sqlFor(engine).firstRow(script("average-customer")).values().first()) == dec("126193.54")

        and: "two-averages: 'over two hundred times apart' — 212.04"
        new BigDecimal("126193.54").divide(new BigDecimal("595.13"), 2, RoundingMode.HALF_UP) == new BigDecimal("212.04")

        and: "thirty's card: 30 rows, the top three and the last two above the line"
        thirty.size() == 30
        thirty.take(3).collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Fjord Foods", dec("797164")], ["Yarrow Pantry", dec("786022.56")],
                 ["Quayside Traders", dec("783579.03")]]
        thirty.takeRight(2).collect { [it.CompanyName, dec(it["Total sales"])] } ==
                [["Amber Grocers", dec("186853.36")], ["Emerald Grocers", dec("175249.7")]]

        and: "the first customer BELOW the line is Kestrel Pantry on 121607.73, as Leo says"
        all[30].CompanyName == "Kestrel Pantry"
        dec(all[30]["Total sales"]) == dec("121607.73")

        and: "THE SAME OUTER QUERY, ONE BRACKET CHANGED: the thirty are exactly the top thirty of the 118"
        thirty*.CompanyName == all.take(30)*.CompanyName

        where:
        engine << ENGINES
    }

    // --- 4b. THE CASE SEGMENT: 2024, ordered and invoiced ----------------------------------------
    // The video's case-* slides and the article's "Ordered, and invoiced". Figures LOCKED in
    // .docs/plan-academy-figures-northwind-co-s.md (column C) and asserted here on both engines.

    @Unroll
    def "[#engine] two values side by side: 2024 ordered 3628572.55, invoiced 3190372.69"() {
        given:
        def rows = sqlFor(engine).rows(script("ordered-and-invoiced-2024"))

        expect: "case-two-totals' card: one row, the two values"
        rows.size() == 1
        dec(rows[0]["Ordered 2024"]) == dec("3628572.55")
        dec(rows[0]["Invoiced 2024"]) == dec("3190372.69")

        and: "NOTHING NEW IN THE SQL: two value subqueries in the select list, and no FROM on the outer query"
        def ls = lines(script("ordered-and-invoiced-2024"))
        ls.count { it.contains("(SELECT ") } == 2
        ls.count { it =~ /^\s*FROM / } == 2

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the manager's note is true: orders up about 10% on 2023, invoicing down"() {
        // The note is SAID on case-the-note, never computed on screen (a growth query is four values,
        // too much for the segment). Asserted so the sentence cannot be false.
        given:
        def y23 = sqlFor(engine).firstRow('''SELECT
              (SELECT ROUND(SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")), 2)
               FROM "Order Details" d JOIN "Orders" o ON o."OrderID" = d."OrderID"
               WHERE o."OrderDate" >= DATE '2023-01-01' AND o."OrderDate" < DATE '2024-01-01') AS ordered,
              (SELECT ROUND(SUM("Amount"), 2) FROM "Invoices"
               WHERE "InvoiceDate" >= DATE '2023-01-01' AND "InvoiceDate" < DATE '2024-01-01') AS invoiced''')

        expect:
        dec(y23.ordered) == dec("3282259.91")
        dec(y23.invoiced) == dec("3245430.61")
        new BigDecimal("3628572.55").divide(new BigDecimal("3282259.91"), 3, RoundingMode.HALF_UP) == new BigDecimal("1.106")
        dec("3190372.69") < dec("3245430.61")

        where:
        engine << ENGINES
    }

    // --- 5. What the KOANS stand on --------------------------------------------------------------

    @Unroll
    def "[#engine] the koan comments' facts are true"() {
        expect: "koan 1: the average freight is 69.31"
        dec(sqlFor(engine).firstRow('SELECT ROUND(AVG("Freight"), 2) AS a FROM "Orders"').a) == dec("69.31")

        and: "koan 2: exactly one order carries the highest freight"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "Freight" = (SELECT MAX("Freight") FROM "Orders")').n == 1

        and: "koan 3: Ines Torres (9) took 1,125 orders, and the two orders that beat all of them are not hers"
        sqlFor(engine).firstRow('SELECT "FirstName" || \' \' || "LastName" AS n FROM "Employees" WHERE "EmployeeID" = 9').n == "Ines Torres"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "EmployeeID" = 9').n == 1125
        sqlFor(engine).rows('''SELECT "EmployeeID" AS e FROM "Orders"
                               WHERE "Freight" > (SELECT MAX("Freight") FROM "Orders" WHERE "EmployeeID" = 9)''')
                .with { it.size() == 2 && it.every { r -> r.e != 9 } }

        and: "koan 4 and 5: ZENIT is Zenith Fine Foods, and it bought 6 different products on 8 order lines"
        sqlFor(engine).firstRow("SELECT \"CompanyName\" AS n FROM \"Customers\" WHERE \"CustomerID\" = 'ZENIT'").n == "Zenith Fine Foods"
        sqlFor(engine).firstRow('''SELECT count(*) AS n, count(DISTINCT d."ProductID") AS p FROM "Order Details" d
                                   JOIN "Orders" o ON o."OrderID" = d."OrderID"
                                   WHERE o."CustomerID" = 'ZENIT' ''').with { it.n == 8 && it.p == 6 }

        and: "koan 6: without the GROUP BY the 'average supplier' is the whole business, 15017031.15"
        dec(sqlFor(engine).firstRow('''SELECT ROUND(AVG(t."Total"), 2) AS a
                                       FROM (SELECT SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
                                             FROM "Products" p
                                             JOIN "Order Details" d ON d."ProductID" = p."ProductID") AS t''').a) == dec("15017031.15")

        and: "koan 7: nine of twenty — and against the average order line, all twenty suppliers would pass"
        sqlFor(engine).firstRow('SELECT count(DISTINCT "SupplierID") AS n FROM "Products"').n == 20
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM (
                                     SELECT p."SupplierID" FROM "Products" p
                                     JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                     GROUP BY p."SupplierID"
                                     HAVING SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount"))
                                          > (SELECT AVG("UnitPrice" * "Quantity" * (1 - "Discount")) FROM "Order Details")) t''').n == 20

        and: "koan 8: there is no employee 99 (the company has 29), so MAX over their orders is one row holding NULL"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Employees" WHERE "EmployeeID" = 99').n == 0
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "EmployeeID" = 99').n == 0
        sqlFor(engine).firstRow('SELECT MAX("Freight") AS m FROM "Orders" WHERE "EmployeeID" = 99').m == null

        and: "koan 9: nine reps took orders, two share a surname, the average rep sold 1668559.02, and the fifth misses it by under 26,000"
        sqlFor(engine).firstRow('SELECT count(DISTINCT "EmployeeID") AS n FROM "Orders"').n == 9
        sqlFor(engine).firstRow('SELECT min("EmployeeID") AS n FROM "Orders"').n == 4
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Employees" WHERE "LastName" = \'Keller\' AND "Title" = \'Sales Representative\'').n == 2
        def reps = sqlFor(engine).rows('''SELECT o."EmployeeID", SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS t
                                          FROM "Orders" o JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                                          GROUP BY o."EmployeeID" ORDER BY t DESC''').collect { dec(it.t) }
        def avg = dec(sqlFor(engine).firstRow('''SELECT ROUND(AVG(t), 2) AS a FROM (
                                          SELECT SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS t
                                          FROM "Orders" o JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                                          GROUP BY o."EmployeeID") x''').a)
        avg == dec("1668559.02")
        reps[4] < avg && avg - reps[4] < 26000

        and: "koan 10: the dearest product is ONE product, so '= (SELECT ...)' is safe; its category has ten products from eight suppliers"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Products"
                                   WHERE "UnitPrice" = (SELECT MAX("UnitPrice") FROM "Products")''').n == 1
        sqlFor(engine).firstRow('''SELECT count(*) AS n, count(DISTINCT "SupplierID") AS s FROM "Products"
                                   WHERE "CategoryID" = (SELECT "CategoryID" FROM "Products"
                                                         WHERE "UnitPrice" = (SELECT MAX("UnitPrice") FROM "Products"))''')
                .with { it.n == 10 && it.s == 8 }

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
        [engine, title] << [ENGINES, ANSWERS.findAll { it.value != null }.keySet() as List].combinations()
    }

    @Unroll
    def "[#engine] koan 8 (predict), as written: three queries that run, and the numbers the comment states"() {
        given:
        def title = "predict: a subquery that finds nothing raises no error and returns no rows"
        def queries = koanQueries(title)

        expect: "three queries, three blanks, and the blanks are the three row counts 0, 1 and 0"
        queries.size() == 3
        koanBody(title).count("== ___") == 3
        queries.collect { sqlFor(engine).rows(it).size() } == [0, 1, 0]

        and: "run alone, the MAX brackets hand back ONE row holding NULL"
        sqlFor(engine).firstRow(queries[1]).getAt(0) == null

        where:
        engine << ENGINES
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
        titles[7] == "predict: a subquery that finds nothing raises no error and returns no rows"
        koanQueries(titles[8])*.trim() == ["___"]
        koanQueries(titles[9])*.trim() == ["___"]
        koansSource().contains("extends NorthwindCoKoanBase")
    }

    @IgnoreIf({ !SubqueriesSpec.videoPresent() })
    def "the video's editor mock quotes koan 1 verbatim"() {
        // ED_CODE and animFill in the video file hard-code koan 1's blank line and its expected
        // value; if the koan moves, the mock would draw a line the file no longer has.
        expect:
        koanBody("a value in brackets: freight above the average")
                .contains('WHERE "Freight" > (SELECT ___("Freight") FROM "Orders")')
        koanBody("a value in brackets: freight above the average").contains("shouldReturn 3029, '''")
        koanBody("a value in brackets can be the whole comparison").contains('shouldReturn([[567, "BRAM2", 726.54]], \'\'\'')
    }

    // --- 7. THE QUERY TEXT: the video's SQL, the scripts' SQL and the koans' SQL --------------------

    /** The scripts the video draws, by name — every one must be on screen, as written. */
    private static final List<String> SCRIPTS_IN_VIDEO = [
            "avg-price", "above-average-price-typed", "aggregate-in-where", "above-average-price",
            "dearer-than-beverages-error", "dearer-than-every-beverage", "royal-raisins-customers",
            "average-sale", "customers-above-the-average-line", "average-customer",
            "customers-above-the-average-customer", "ordered-and-invoiced-2024"]

    /** The two queries only the video shows (no script, no article block). */
    private static final String VIDEO_NO_CATEGORY_NINE = '''SELECT "ProductName", "UnitPrice"
FROM "Products"
WHERE "UnitPrice" > (SELECT MAX("UnitPrice")
                     FROM "Products"
                     WHERE "CategoryID" = 9)
ORDER BY "UnitPrice" DESC;'''
    private static final String VIDEO_AVERAGE_CUSTOMER_BESIDE_THE_LINE = '''SELECT ROUND(AVG(t."Total"), 2) AS "Average"
FROM (SELECT o."CustomerID",
             SUM(d."UnitPrice" * d."Quantity"
               * (1 - d."Discount")) AS "Total"
      FROM "Orders" o
      JOIN "Order Details" d ON d."OrderID" = o."OrderID"
      GROUP BY o."CustomerID") AS t;'''

    @IgnoreIf({ !SubqueriesSpec.videoPresent() })
    def "every SQL block the video draws is a lesson script, or one of the two the video alone shows"() {
        given:
        def drawn = videoQueries()
        def allowed = (SCRIPTS_IN_VIDEO.collect { norm(script(it)) } +
                       [norm(VIDEO_NO_CATEGORY_NINE), norm(VIDEO_AVERAGE_CUSTOMER_BESIDE_THE_LINE)]) as Set

        expect: "the video draws SQL at all"
        drawn.size() >= 19

        and: "nothing on screen is a query the gate has not run"
        drawn.findAll { !(norm(it) in allowed) }.isEmpty()

        and: "and every script the video uses is on screen as written"
        SCRIPTS_IN_VIDEO.every { name -> drawn.any { norm(it) == norm(script(name)) } }
        drawn.any { norm(it) == norm(VIDEO_NO_CATEGORY_NINE) }
        drawn.any { norm(it) == norm(VIDEO_AVERAGE_CUSTOMER_BESIDE_THE_LINE) }
    }

    @IgnoreIf({ !SubqueriesSpec.videoPresent() })
    def "the hands-on card's query is the at-or-below script with its one blank filled"() {
        given: "hands-on-average draws `... <= ___;` and the learner fills the brackets in"
        def card = videoArray("ctaSql").find { it.contains("___") }

        expect:
        card != null
        norm(card.replace("___", '(SELECT AVG("UnitPrice") FROM "Products")')) == norm(script("at-or-below-average-price"))
    }

    def "the royal-raisins scripts ask for 2024 as a half-open range, as the video does"() {
        expect: "a lone >= would let 2025 in; the dataset ends in 2024, so both agree, but the text is the video's"
        script("royal-raisins-customers").contains("""o."OrderDate" <  DATE '2025-01-01'""")
        script("royal-raisins-customers-joined").contains("""o."OrderDate" <  DATE '2025-01-01'""")
    }

    @Unroll
    def "[#engine] the video-only queries return what the video says"() {
        expect: "no-rows-no-error: category 9 does not exist (1 to 8, ten products each), the inside is NULL, the whole query is 0 rows"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Categories" WHERE "CategoryID" = 9').n == 0
        sqlFor(engine).firstRow('SELECT count(DISTINCT "CategoryID") AS n FROM "Products"').n == 8
        sqlFor(engine).rows('SELECT "CategoryID" AS c FROM "Products" GROUP BY "CategoryID" HAVING count(*) <> 10').isEmpty()
        sqlFor(engine).firstRow('SELECT MAX("UnitPrice") AS m FROM "Products" WHERE "CategoryID" = 9').m == null
        sqlFor(engine).rows(VIDEO_NO_CATEGORY_NINE).isEmpty()

        and: "two-averages: 126193.54 beside 595.13, the same money counted per customer and per line"
        dec(sqlFor(engine).firstRow(VIDEO_AVERAGE_CUSTOMER_BESIDE_THE_LINE).Average) == dec("126193.54")
        dec(sqlFor(engine).firstRow(VIDEO_AVERAGE_CUSTOMER_BESIDE_THE_LINE).Average) ==
                dec(sqlFor(engine).firstRow(script("average-customer")).values().first())

        where:
        engine << ENGINES
    }

    @IgnoreIf({ !SubqueriesSpec.videoPresent() })
    def "the video quotes the koan names and koan 1 as the file has them"() {
        given:
        def video = videoSource()
        def titles = (koansSource() =~ /(?m)^    def "(.+?)"\(\)/).collect { it[1] }

        expect: "do-koans' checklist and the editor mock use the koans' own names and lines"
        [0, 1, 2].every { video.contains('name: "' + titles[it] + '"') }
        video.contains('WHERE \\"Freight\\" > (SELECT ___(\\"Freight\\") FROM \\"Orders\\")')
        video.contains("<b>10</b> koans")
        titles.size() == 10
    }

    // ANSI: the dialect words and shorthand that do not run on every one of the nine databases.
    private static final Map<String, String> NOT_ANSI = [
            "LIMIT"                              : /(?i)\bLIMIT\b/,
            "TOP n"                              : /(?i)\bSELECT\s+(DISTINCT\s+)?TOP\b/,
            "double-colon cast"                  : /::/,
            "backtick or [bracket] identifier"   : /[`\[]/,
            "ILIKE / REGEXP / RLIKE"             : /(?i)\b(ILIKE|REGEXP|RLIKE)\b/,
            "IFNULL / ISNULL / NVL"              : /(?i)\b(IFNULL|ISNULL|NVL)\b/,
            "vendor date functions"              : /(?i)\b(NOW|GETDATE|DATEADD|DATEDIFF|DATE_TRUNC|DATE_ADD|STRFTIME|TO_CHAR|TO_DATE|YEAR|MONTH)\s*\(/,
            "GROUP/ORDER BY ALL, EXCLUDE, QUALIFY": /(?i)\b(GROUP\s+BY\s+ALL|ORDER\s+BY\s+ALL|EXCLUDE|QUALIFY)\b/,
            "unquoted table name"                : /(?i)\b(FROM|JOIN)\s+[A-Za-z_]/,
            "bare date literal"                  : /(?<!DATE\s)'\d{4}-\d{2}-\d{2}'/,
    ]

    private static List<String> ansiViolations(String sql) {
        def text = sql.replace("___", "")   // a koan blank is not SQL
        NOT_ANSI.findAll { why, rx -> text.find(rx) != null }.keySet() as List
    }

    def "every query the scripts, the video and the koans run is portable ANSI SQL"() {
        given:
        def scripts = new File("../courses/learnsql/series2-intermediate/00-subqueries/scripts").listFiles()
                .findAll { it.name.endsWith(".sql") }.collectEntries { [it.name, it.getText("UTF-8")] }
        def video = [:]
        videoQueries().eachWithIndex { q, i -> video["video block ${i + 1}".toString()] = q }
        def koans = [:]
        (koansSource() =~ /(?s)'''(.*?)'''/).collect { it[1] }.eachWithIndex { q, i -> koans["koan query ${i + 1}".toString()] = q }
        def answers = [:]
        ANSWERS.findAll { it.value != null }.each { t, a -> answers["answer to '${t}'".toString()] = koanSql(t) }

        expect: "the sources are all there"
        scripts.size() >= 14
        !videoPresent() || video.size() >= 19
        koans.size() >= 10
        answers.size() == 9

        and: "none of them uses a construct outside the standard"
        (scripts + video + koans + answers).collectEntries { name, q -> [name, ansiViolations(q)] }
                .findAll { it.value }.isEmpty()

        and: "the lint itself bites: dialect SQL is caught"
        ansiViolations('SELECT * FROM "Products" LIMIT 3') == ["LIMIT"]
        ansiViolations('SELECT x::int FROM "Products"') == ["double-colon cast"]
        ansiViolations('SELECT * FROM Products') == ["unquoted table name"]
        ansiViolations("""SELECT 1 FROM "Orders" WHERE "OrderDate" >= '2024-01-01'""") == ["bare date literal"]
        ansiViolations("""SELECT 1 FROM "Orders" WHERE "OrderDate" >= DATE '2024-01-01'""").isEmpty()
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static String norm(String sql) { sql.replaceAll(/\s+/, " ").replaceAll(/\s*;\s*$/, "").trim() }

    // The video lives in the cli-remotion project, a sibling of this repo in the monorepo. A standalone
    // clone of this repo has no video to read, so every check that reads it skips instead of failing.
    private static final File VIDEO_FILE = new File("../../../../cli-remotion/src/videos/rb/learnsql-series2-00-subqueries/index.tsx")

    static boolean videoPresent() { VIDEO_FILE.exists() }

    private static String videoSource() { VIDEO_FILE.exists() ? VIDEO_FILE.getText("UTF-8") : "" }

    /** The string-literal lines of every `<key>: [` array in the video, each array joined as one query. */
    private static List<String> videoArray(String key) {
        def out = []
        def lines = videoSource().readLines()
        for (int i = 0; i < lines.size(); i++) {
            if (!(lines[i] =~ /^\s*${key}: \[\s*$/)) continue
            def parts = []
            for (int j = i + 1; j < lines.size() && !(lines[j] =~ /^\s*\],?\s*$/); j++) {
                def m = lines[j] =~ /^\s*(['"])(.*)\1,?\s*$/
                if (m.matches()) parts << unescapeJs(m.group(2))
            }
            out << parts.join("\n")
        }
        out
    }

    /** Every `sql: [ ... ]` block the video draws (scene cards, the monster view, both twoRoutes sides, the before-card). */
    private static List<String> videoQueries() { videoArray("sql") }

    private static String unescapeJs(String s) {
        s.replace("\\\\", "\u0000").replace("\\'", "'").replace('\\"', '"').replace("\u0000", "\\")
    }

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
            "predict: a subquery that finds nothing raises no error and returns no rows" : null,
            "write the whole query: sales reps who beat the average sales rep"    : ['''
                SELECT e."FirstName", e."LastName",
                       ROUND(SUM(d."UnitPrice" * d."Quantity"
                         * (1 - d."Discount")), 2) AS "Total sales"
                FROM "Employees" e
                JOIN "Orders" o ON o."EmployeeID" = e."EmployeeID"
                JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                GROUP BY e."EmployeeID", e."FirstName", e."LastName"
                HAVING SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount"))
                     > (SELECT AVG(t."Total")
                        FROM (SELECT o2."EmployeeID",
                                     SUM(d2."UnitPrice" * d2."Quantity"
                                       * (1 - d2."Discount")) AS "Total"
                              FROM "Orders" o2
                              JOIN "Order Details" d2 ON d2."OrderID" = o2."OrderID"
                              GROUP BY o2."EmployeeID") AS t)
                ORDER BY "Total sales" DESC
            '''],
            "write the whole query: a value inside a value inside a list"         : ['''
                SELECT "SupplierID", "CompanyName"
                FROM "Suppliers"
                WHERE "SupplierID" IN (
                  SELECT "SupplierID"
                  FROM "Products"
                  WHERE "CategoryID" = (SELECT "CategoryID" FROM "Products"
                                        WHERE "UnitPrice" = (SELECT MAX("UnitPrice") FROM "Products"))
                )
                ORDER BY "SupplierID"
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
