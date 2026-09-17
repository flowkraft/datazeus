package datazeus.learnsql.series2._15

import datazeus.support.NorthwindCoGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 2 · lesson _15
 * "Multi-Table JOINs & Grain — When a Join Silently Drops or Multiplies Your Rows".
 *
 * Runs on NORTHWIND COMPANY S (schema northwind_co_s) on BOTH engines, each re-checksummed against
 * `_dataset_info` before any assertion (NorthwindCoGateSpec). Every figure the video, the article,
 * the trailer, the short and the koans put in front of a learner is asserted here. The lesson's
 * scripts, and the sections that run them:
 *
 *    dairy-units-left-then-inner, dairy-units-left-all-the-way,
 *    dairy-units-narrowed-first, dairy-products-without-may-sale              §1 a join that drops rows
 *    sales-and-freight-per-customer, freight-per-customer,
 *    nordic-foods-orders-and-lines, company-freight-two-ways                   §2 a join that multiplies money
 *    sum-distinct-freight, orders-and-freight-values                           §3 SUM(DISTINCT) is wrong
 *    sales-and-freight-aggregated-first, heritage-butter-lines-and-customers   §4 the cure, and the bridge
 *    shipped-orders-without-invoice                                            §4b the case file
 *
 * §5 asserts the data facts the lesson leans on and every number the KOANS' comments state, and
 * §6 runs the koans file itself, as written, on both engines (ported from Series 1 · 50 §8).
 *
 * ── THE CASE FILE (plan-academy-course-stories-artefacts.md §3.2.1, row 15; figures LOCKED in
 *    plan-academy-figures-northwind-co-s.md, column C) ─────────────────────────────────────────
 * Each order's value aggregated first, then LEFT JOIN "Invoices", shipped orders with no invoice:
 * 188 orders, 275698.44. The value is ROUNDed once, on the SUM (rounded per order: 275698.52).
 * Joined to the lines directly, count(*) reads 460 — lines, not orders.
 *
 * ── THE EARN, AS ARITHMETIC ────────────────────────────────────────────────────────────────
 * May 2024, Nordic Foods: 184.43 × 5 + 59.69 × 3 + 44.27 × 2 + 191.90 × 5 = 2149.26, against 480.29
 * over "Orders" alone; number one moves from Alpine Provisions (610.83) to Nordic Foods.
 * The company, all five years: 527429.46 against 1712919.25. SUM(DISTINCT) gives 391726.53 —
 * 135702.93 short — because 10000 orders have only 6689 different freight values.
 *
 * ── WHY MAY 2024 AND THE DAIRY PRODUCTS (measured 2026-09-17) ───────────────────────────────
 * Over all five years every product has sold, and over any whole month of 2024 60 to 69 of the 80
 * products sell — a result too long to read. One category in one month keeps the whole result on a
 * card: Dairy Products in May 2024, 7 of 10. Per customer over all five years the fan-out does NOT
 * move number one (Fjord Foods both ways); in May 2024 it does. Both asserted below.
 *
 * ── ORDER AND NAMES ────────────────────────────────────────────────────────────────────────
 * The Dairy product lists are ordered by "ProductName"; the ten names sort alike in byte order
 * (DuckDB) and PostgreSQL's collation — asserted by pinning the full list on both. The top-five
 * lists are ordered by money, all values different, and the sixth row differs from the fifth, so
 * LIMIT 5 cuts in the same place on both engines.
 */
class MultiTableJoinsDuplicateRowsSpec extends NorthwindCoGateSpec {

    static final List<String> DAIRY = ["Alpine Butter", "Classic Feta", "Coastal Parmesan", "Harvest Butter", "Heritage Butter",
                                       "Heritage Gouda", "Highland Yoghurt", "Island Cheddar", "Rustic Butter", "Smoked Feta"]

    // --- 1. A JOIN THAT DROPS ROWS ------------------------------------------------------------------

    @Unroll
    def "[#engine] LEFT JOIN then JOIN for May: 7 rows of the 10 Dairy products"() {
        given:
        def rows = sqlFor(engine).rows(script("dairy-units-left-then-inner"))

        expect: "seven-rows' card"
        rows.collect { [it.ProductName, it["May units"] as int] } ==
                [["Coastal Parmesan", 10], ["Harvest Butter", 408], ["Heritage Butter", 836], ["Heritage Gouda", 58],
                 ["Highland Yoghurt", 52], ["Rustic Butter", 471], ["Smoked Feta", 56]]

        and: "ten products went in: category 4 is Dairy Products"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "CategoryID" = 4').n == 10
        sqlFor(engine).firstRow('SELECT "CategoryName" AS n FROM "Categories" WHERE "CategoryID" = 4').n == "Dairy Products"

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] LEFT all the way: 10 rows, Heritage Butter 47743, and the units add up to 109446 — May's real total is 1891"() {
        given:
        def rows = sqlFor(engine).rows(script("dairy-units-left-all-the-way"))

        expect: "looks-repaired's card, whole"
        rows.collect { [it.ProductName, it["May units"] as int] } ==
                [["Alpine Butter", 1819], ["Classic Feta", 2991], ["Coastal Parmesan", 1759], ["Harvest Butter", 20002],
                 ["Heritage Butter", 47743], ["Heritage Gouda", 1562], ["Highland Yoghurt", 2107], ["Island Cheddar", 3436],
                 ["Rustic Butter", 26813], ["Smoked Feta", 1214]]
        rows.collect { it["May units"] as int }.sum() == 109446

        and: "109446 is every Dairy unit ever sold — the May test in the ON did not filter a single line"
        sqlFor(engine).firstRow('''SELECT sum(d."Quantity") AS n FROM "Order Details" d
                                   JOIN "Products" p ON p."ProductID" = d."ProductID" WHERE p."CategoryID" = 4''').n as int == 109446

        and: "the product names sort alike on both engines"
        rows*.ProductName == DAIRY

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] narrowed first: 10 rows, 7 with May units, totalling 1891, and 3 without a May sale"() {
        given:
        def rows = sqlFor(engine).rows(script("dairy-units-narrowed-first"))
        def sold = rows.findAll { it["May units"] != null }

        expect: "ten-seven's card, whole"
        rows.collect { [it.ProductName, it["May units"] == null ? null : (it["May units"] as int)] } ==
                [["Alpine Butter", null], ["Classic Feta", null], ["Coastal Parmesan", 10], ["Harvest Butter", 408],
                 ["Heritage Butter", 836], ["Heritage Gouda", 58], ["Highland Yoghurt", 52], ["Island Cheddar", null],
                 ["Rustic Butter", 471], ["Smoked Feta", 56]]
        sold.size() == 7
        sold.collect { it["May units"] as int }.sum() == 1891

        and: "the hands-on: 3"
        (sqlFor(engine).firstRow(script("dairy-products-without-may-sale"))["No May sale"] as int) == 3

        and: "the same 7 products and units as the left-then-inner query"
        sold.collect { [it.ProductName, it["May units"] as int] } ==
                sqlFor(engine).rows(script("dairy-units-left-then-inner")).collect { [it.ProductName, it["May units"] as int] }

        and: "the article: of the three, Classic Feta is discontinued (last sold in March 2024); the other two are not"
        sqlFor(engine).rows('''SELECT "ProductName" AS n, "Discontinued" AS d FROM "Products"
                               WHERE "ProductName" IN ('Alpine Butter', 'Classic Feta', 'Island Cheddar') ORDER BY "ProductID"''')
                .collect { [it.n, it.d as boolean] } == [["Alpine Butter", false], ["Island Cheddar", false], ["Classic Feta", true]]

        where:
        engine << ENGINES
    }

    // --- 2. A JOIN THAT MULTIPLIES MONEY --------------------------------------------------------------

    @Unroll
    def "[#engine] May 2024 joined: Nordic Foods first on 2149.26 — over Orders alone Alpine Provisions leads on 610.83"() {
        given:
        def joined = sqlFor(engine).rows(script("sales-and-freight-per-customer"))
        def alone = sqlFor(engine).rows(script("freight-per-customer"))

        expect: "freight-top's card"
        joined.collect { [it.CompanyName, dec(it["Total sales"]), dec(it.Freight)] } == [
                ["Nordic Foods", dec("11322.73"), dec("2149.26")],
                ["Golden Pantry", dec("18342.33"), dec("2050.36")],
                ["Alpine Provisions", dec("15686.56"), dec("1814.54")],
                ["Nordic Delicatessen", dec("12531.83"), dec("1317.29")],
                ["Juniper Trading", dec("11928.18"), dec("1226.41")]]

        and: "orders-alone-result's card: Nordic Foods is fifth"
        alone.collect { [it.CompanyName, dec(it.Freight)] } == [
                ["Alpine Provisions", dec("610.83")], ["Golden Pantry", dec("562.08")], ["Yarrow Pantry", dec("502.33")],
                ["Quayside Traders", dec("497.47")], ["Nordic Foods", dec("480.29")]]

        and: "LIMIT 5 cuts cleanly: the sixth row of each list is below the fifth"
        dec(sqlFor(engine).rows(script("sales-and-freight-per-customer").replace("LIMIT 5;", "LIMIT 6;"))[5].Freight) < dec("1226.41")
        dec(sqlFor(engine).rows(script("freight-per-customer").replace("LIMIT 5;", "LIMIT 6;"))[5].Freight) < dec("480.29")

        and: "GROUP BY \"CompanyName\" is one group per customer: the 120 names are unique"
        sqlFor(engine).firstRow('SELECT count(*) AS n, count(DISTINCT "CompanyName") AS d FROM "Customers"').with { [it.n as int, it.d as int] } == [120, 120]

        and: "Nordic Foods placed 4 orders in May 2024, Alpine Provisions 13"
        sqlFor(engine).rows('''SELECT "CustomerID" AS c, count(*) AS n FROM "Orders"
                               WHERE "OrderDate" >= DATE '2024-05-01' AND "OrderDate" < DATE '2024-06-01'
                                 AND "CustomerID" IN ('NORDI', 'ALPI2') GROUP BY "CustomerID" ORDER BY "CustomerID"''')
                .collect { [it.c, it.n as int] } == [["ALPI2", 13], ["NORDI", 4]]
        sqlFor(engine).firstRow('''SELECT "CompanyName" AS n FROM "Customers" WHERE "CustomerID" = 'NORDI' ''').n == "Nordic Foods"

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] why: Nordic Foods' May orders carry 184.43, 59.69, 44.27, 191.9 on 5, 3, 2, 5 lines — 2149.26"() {
        given:
        def rows = sqlFor(engine).rows(script("nordic-foods-orders-and-lines"))

        expect: "why-multiplied's card"
        rows.collect { [it.OrderID as int, dec(it.Freight), it.Lines as int] } ==
                [[8374, dec("184.43"), 5], [8468, dec("59.69"), 3], [8482, dec("44.27"), 2], [8548, dec("191.9"), 5]]

        and: "the arithmetic Mnemosyne says: 15 lines, 2149.26, and the 480.29 it should have been"
        rows.collect { it.Lines as int }.sum() == 15
        rows.collect { new BigDecimal(it.Freight.toString()) * (it.Lines as int) }.sum().stripTrailingZeros() == dec("2149.26")
        rows.collect { new BigDecimal(it.Freight.toString()) }.sum().stripTrailingZeros() == dec("480.29")

        and: "the short's collapse beat: order 8374 alone, 184.43 on five lines, 922.15"
        (new BigDecimal(rows[0].Freight.toString()) * (rows[0].Lines as int)).stripTrailingZeros() == dec("922.15")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the company: 527429.46 over Orders, 1712919.25 after the join — 'more than three times'"() {
        given:
        def r = sqlFor(engine).firstRow(script("company-freight-two-ways"))

        expect:
        dec(r["Orders alone"]) == dec("527429.46")
        dec(r["After the join"]) == dec("1712919.25")
        (new BigDecimal("1712919.25") / new BigDecimal("527429.46")) > 3.2
        (new BigDecimal("1712919.25") / new BigDecimal("527429.46")) < 3.3

        and: "all five years: the whole table"
        sqlFor(engine).firstRow('SELECT min("OrderDate") AS a, max("OrderDate") AS b, count(*) AS n FROM "Orders"')
                .with { [it.a.toString().take(10), it.b.toString().take(10), it.n as int] } == ["2020-01-01", "2024-12-31", 10000]

        where:
        engine << ENGINES
    }

    // --- 3. SUM(DISTINCT) IS WRONG ---------------------------------------------------------------------

    @Unroll
    def "[#engine] SUM(DISTINCT) gives 391726.53 — 135702.93 short — because 10000 orders share 6689 freight values"() {
        expect: "distinct-is-wrong's card"
        dec(sqlFor(engine).firstRow(script("sum-distinct-freight"))["SUM(DISTINCT)"]) == dec("391726.53")
        new BigDecimal("527429.46") - new BigDecimal("391726.53") == new BigDecimal("135702.93")

        and: "freight-values' card"
        sqlFor(engine).firstRow(script("orders-and-freight-values")).with { [it.Orders as int, it["Freight values"] as int] } == [10000, 6689]

        and: "SUM(DISTINCT) over the orders alone is the same wrong number: the join is not what breaks it"
        dec(sqlFor(engine).firstRow('SELECT SUM(DISTINCT "Freight") AS s FROM "Orders"').s) == dec("391726.53")

        and: "the article: on May 2024 alone it is 140.41 short — 8837.47 against 8977.88 (181 orders, 178 values)"
        def may = sqlFor(engine).firstRow('''SELECT SUM(DISTINCT o."Freight") AS d FROM "Orders" o
                                             JOIN "Order Details" dd ON dd."OrderID" = o."OrderID"
                                             WHERE o."OrderDate" >= DATE '2024-05-01' AND o."OrderDate" < DATE '2024-06-01' ''')
        dec(may.d) == dec("8837.47")
        // Read the row first: inside .with { } on a GroovyRowResult, dec(...) would resolve against the row.
        def mayOrders = sqlFor(engine).firstRow('''SELECT SUM("Freight") AS s, count(*) AS n, count(DISTINCT "Freight") AS v FROM "Orders"
                                                   WHERE "OrderDate" >= DATE '2024-05-01' AND "OrderDate" < DATE '2024-06-01' ''')
        [dec(mayOrders.s), mayOrders.n as int, mayOrders.v as int] == [dec("8977.88"), 181, 178]

        where:
        engine << ENGINES
    }

    // --- 4. THE CURE, AND THE BRIDGE -------------------------------------------------------------------

    @Unroll
    def "[#engine] aggregated first: Alpine Provisions first again on 610.83, and every sales total unchanged"() {
        given:
        def cure = sqlFor(engine).rows(script("sales-and-freight-aggregated-first"))
        def joined = sqlFor(engine).rows(script("sales-and-freight-per-customer").replace("LIMIT 5;", ""))
                .collectEntries { [(it.CompanyName): dec(it["Total sales"])] }

        expect: "cure-result's card"
        cure.collect { [it.CompanyName, dec(it["Total sales"]), dec(it.Freight)] } == [
                ["Alpine Provisions", dec("15686.56"), dec("610.83")],
                ["Golden Pantry", dec("18342.33"), dec("562.08")],
                ["Yarrow Pantry", dec("12884.6"), dec("502.33")],
                ["Quayside Traders", dec("13170.94"), dec("497.47")],
                ["Nordic Foods", dec("11322.73"), dec("480.29")]]

        and: "'every sales total is exactly what it was' — the joined report's sales were never wrong"
        cure.every { joined[it.CompanyName] == dec(it["Total sales"]) }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the bridge: Heritage Butter on 779 lines, to 78 customers"() {
        expect:
        sqlFor(engine).firstRow(script("heritage-butter-lines-and-customers")).with { [it.Lines as int, it.Customers as int] } == [779, 78]
        sqlFor(engine).firstRow('SELECT "ProductName" AS n, "CategoryID" AS c FROM "Products" WHERE "ProductID" = 28')
                .with { [it.n, it.c as int] } == ["Heritage Butter", 4]

        and: "a product appears at most once per order, so 779 lines are 779 orders"
        sqlFor(engine).firstRow('''SELECT count(*) AS n, count(DISTINCT "OrderID") AS o FROM "Order Details" WHERE "ProductID" = 28''')
                .with { [it.n as int, it.o as int] } == [779, 779]

        where:
        engine << ENGINES
    }

    // --- 4b. THE CASE FILE: SHIPPED ORDERS WITH NO INVOICE, AND HOW MUCH ------------------------------

    @Unroll
    def "[#engine] case file: 188 shipped orders with no invoice, worth 275698.44"() {
        given:
        def r = sqlFor(engine).firstRow(script("shipped-orders-without-invoice"))

        expect: "case-file-result's card"
        [r.Orders as int, dec(r.Value)] == [188, dec("275698.44")]

        and: "\"Invoices\" is one row per order, so the LEFT JOIN cannot multiply anything"
        sqlFor(engine).firstRow('SELECT count(*) AS n, count(DISTINCT "OrderID") AS d FROM "Invoices"')
                .with { [it.n as int, it.d as int] } == [9532, 9532]

        and: "'join the lines in directly and count(*) says 460' — lines, not orders; the value is the same"
        def direct = sqlFor(engine).firstRow('''SELECT count(*) AS n, count(DISTINCT o."OrderID") AS o,
                                                       ROUND(SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")), 2) AS v
                                                FROM "Orders" o
                                                JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                                                LEFT JOIN "Invoices" i ON i."OrderID" = o."OrderID"
                                                WHERE o."Status" = 'Shipped' AND i."InvoiceID" IS NULL''')
        [direct.n as int, direct.o as int, dec(direct.v)] == [460, 188, dec("275698.44")]

        and: "the value is rounded once, on the SUM — rounded per order it would read 275698.52"
        dec(sqlFor(engine).firstRow('''SELECT SUM(v.s) AS s FROM "Orders" o
                                       JOIN (SELECT "OrderID", ROUND(SUM("UnitPrice" * "Quantity" * (1 - "Discount")), 2) AS s
                                             FROM "Order Details" GROUP BY "OrderID") v ON v."OrderID" = o."OrderID"
                                       LEFT JOIN "Invoices" i ON i."OrderID" = o."OrderID"
                                       WHERE o."Status" = 'Shipped' AND i."InvoiceID" IS NULL''').s) == dec("275698.52")

        and: "the locked figures sheet: 177 of them in the outage window (Speedy Express, placed 2024-03-04 .. 2024-08-30), 256894.67"
        def outage = sqlFor(engine).firstRow('''SELECT count(*) AS n, ROUND(SUM(v."Order value"), 2) AS v
                                                FROM "Orders" o
                                                JOIN (SELECT "OrderID", SUM("UnitPrice" * "Quantity" * (1 - "Discount")) AS "Order value"
                                                      FROM "Order Details" GROUP BY "OrderID") AS v ON v."OrderID" = o."OrderID"
                                                LEFT JOIN "Invoices" i ON i."OrderID" = o."OrderID"
                                                WHERE o."Status" = 'Shipped' AND i."InvoiceID" IS NULL
                                                  AND o."ShipVia" = 1
                                                  AND o."OrderDate" >= DATE '2024-03-04' AND o."OrderDate" < DATE '2024-08-31' ''')
        [outage.n as int, dec(outage.v)] == [177, dec("256894.67")]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] why May 2024: over all five years the fan-out leaves number one where it is (Fjord Foods both ways)"() {
        // The episode's header states the measurement; the lesson does not show it.
        expect:
        sqlFor(engine).rows('''SELECT c."CompanyName" AS n, SUM(o."Freight") AS f FROM "Customers" c
                               JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                               JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                               GROUP BY c."CompanyName" ORDER BY f DESC LIMIT 1''')[0].n == "Fjord Foods"
        sqlFor(engine).rows('''SELECT c."CompanyName" AS n, SUM(o."Freight") AS f FROM "Customers" c
                               JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                               GROUP BY c."CompanyName" ORDER BY f DESC LIMIT 1''')[0].n == "Fjord Foods"

        and: "any whole month of 2024 sells 60 to 69 of the 80 products — too many rows for a card"
        sqlFor(engine).rows('''SELECT EXTRACT(MONTH FROM o."OrderDate") AS m, count(DISTINCT d."ProductID") AS p
                               FROM "Order Details" d JOIN "Orders" o ON o."OrderID" = d."OrderID"
                               WHERE o."OrderDate" >= DATE '2024-01-01' AND o."OrderDate" < DATE '2025-01-01'
                               GROUP BY EXTRACT(MONTH FROM o."OrderDate")''')
                .collect { it.p as int }.with { [it.size(), it.min(), it.max()] } == [12, 60, 69]

        where:
        engine << ENGINES
    }

    // --- 5. THE DATA FACTS, AND WHAT THE KOANS STAND ON -----------------------------------------------

    @Unroll
    def "[#engine] the dataset is Northwind Company S, and what has no match: 1 customer, 3 employees, no product, no order"() {
        expect: "the sizes the article quotes"
        ['Orders': 10000, 'Order Details': 25233, 'Products': 80, 'Customers': 120, 'Employees': 12, 'Invoices': 9532].every { t, n ->
            (sqlFor(engine).firstRow("SELECT count(*) AS n FROM \"${t}\"".toString()).n as int) == n
        }

        and: "one customer has never ordered: Yarrow Supermarket"
        sqlFor(engine).rows('''SELECT c."CompanyName" AS n FROM "Customers" c
                               WHERE NOT EXISTS (SELECT 1 FROM "Orders" o WHERE o."CustomerID" = c."CustomerID")''')*.n == ["Yarrow Supermarket"]

        and: "three employees take no orders: the chief executive and the two sales managers"
        sqlFor(engine).rows('''SELECT e."EmployeeID" AS id, e."Title" AS t FROM "Employees" e
                               WHERE NOT EXISTS (SELECT 1 FROM "Orders" o WHERE o."EmployeeID" = e."EmployeeID")
                               ORDER BY e."EmployeeID"''')
                .collect { [it.id as int, it.t] } == [[1, "Chief Executive Officer"], [2, "Sales Manager"], [3, "Sales Manager"]]

        and: "every product has sold, and every order has lines"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Products" p
                                   WHERE NOT EXISTS (SELECT 1 FROM "Order Details" d WHERE d."ProductID" = p."ProductID")''').n == 0
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders" o
                                   WHERE NOT EXISTS (SELECT 1 FROM "Order Details" d WHERE d."OrderID" = o."OrderID")''').n == 0

        and: "an order has between 1 and 6 lines"
        sqlFor(engine).rows('''SELECT DISTINCT n FROM (SELECT count(*) AS n FROM "Order Details" GROUP BY "OrderID") t ORDER BY n''')
                .collect { it.n as int } == [1, 2, 3, 4, 5, 6]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the koan comments' facts are true"() {
        expect: "koan 1: Jonas Novak took 530 orders"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "EmployeeID" = 10').n == 530
        sqlFor(engine).firstRow('SELECT "FirstName" || \' \' || "LastName" AS n FROM "Employees" WHERE "EmployeeID" = 10').n == "Jonas Novak"

        and: "koan 2: the LEFT, LEFT, JOIN version for 30 December 2024 returns 6 rows"
        sqlFor(engine).rows('''SELECT c."CategoryName" FROM "Categories" c
                               LEFT JOIN "Products" p ON p."CategoryID" = c."CategoryID"
                               LEFT JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                               JOIN "Orders" o ON o."OrderID" = d."OrderID"
                                AND o."OrderDate" >= DATE '2024-12-30' AND o."OrderDate" < DATE '2024-12-31'
                               GROUP BY c."CategoryName"''').size() == 6

        and: "koan 3: 30 December 2024's real total is 459 units, and 955168 is every unit ever sold"
        sqlFor(engine).firstRow('''SELECT sum(d."Quantity") AS n FROM "Order Details" d
                                   JOIN "Orders" o ON o."OrderID" = d."OrderID"
                                   WHERE o."OrderDate" >= DATE '2024-12-30' AND o."OrderDate" < DATE '2024-12-31' ''').n as int == 459
        sqlFor(engine).firstRow('SELECT sum("Quantity") AS n FROM "Order Details"').n as int == 955168

        and: "koan 4: the top five countries' freight over Orders alone — Spain above Germany"
        sqlFor(engine).rows('''SELECT c."Country" AS c, SUM(o."Freight") AS f FROM "Customers" c
                               JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                               GROUP BY c."Country" ORDER BY f DESC LIMIT 5''')
                .collect { [it.c, dec(it.f)] } == [["Spain", dec("66716.89")], ["Germany", dec("65131.99")], ["USA", dec("61982.13")],
                                                   ["Finland", dec("58056.81")], ["Brazil", dec("49784.87")]]

        and: "koan 4 and 7: 21 countries, no two with the same freight either way, so LIMIT 5 cuts in the same place on both engines"
        sqlFor(engine).firstRow('''SELECT count(*) AS n, count(DISTINCT f) AS d FROM (SELECT c."Country", SUM(o."Freight") AS f
                                   FROM "Customers" c JOIN "Orders" o ON o."CustomerID" = c."CustomerID" GROUP BY c."Country") t''')
                .with { [it.n as int, it.d as int] } == [21, 21]
        sqlFor(engine).firstRow('''SELECT count(*) AS n, count(DISTINCT f) AS d FROM (SELECT c."Country", SUM(o."Freight") AS f
                                   FROM "Customers" c JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                                   JOIN "Order Details" d ON d."OrderID" = o."OrderID" GROUP BY c."Country") t''')
                .with { [it.n as int, it.d as int] } == [21, 21]

        and: "koan 5: after joining the lines, count(*) per year is 4228, 4564, 4910, 5516 and 6015"
        sqlFor(engine).rows('''SELECT EXTRACT(YEAR FROM o."OrderDate") AS y, count(*) AS n FROM "Orders" o
                               JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                               GROUP BY EXTRACT(YEAR FROM o."OrderDate") ORDER BY y''')
                .collect { [it.y as int, it.n as int] } == [[2020, 4228], [2021, 4564], [2022, 4910], [2023, 5516], [2024, 6015]]

        and: "koan 6: SUM(DISTINCT) over the lines written into the query returns 50.40"
        dec(sqlFor(engine).firstRow('''SELECT SUM(DISTINCT "Freight") AS s
                                       FROM (VALUES (101, 32.00), (101, 32.00), (102, 32.00), (102, 32.00),
                                                    (103, 18.40), (103, 18.40)) AS t("OrderID", "Freight")''').s) == dec("50.40")

        and: "koan 9: 2024's freight summed after joining the lines is 414844.39"
        dec(sqlFor(engine).firstRow('''SELECT SUM(o."Freight") AS f FROM "Orders" o
                                       JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                                       WHERE o."OrderDate" >= DATE '2024-01-01' AND o."OrderDate" < DATE '2025-01-01' ''').f) == dec("414844.39")

        and: "koan 10: counting orders after joining the lines gives Hugo Dubois (12) 990 in 2024"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders" o
                                   JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                                   WHERE o."EmployeeID" = 12
                                     AND o."OrderDate" >= DATE '2024-01-01' AND o."OrderDate" < DATE '2025-01-01' ''').n == 990
        sqlFor(engine).firstRow('SELECT "FirstName" || \' \' || "LastName" AS n FROM "Employees" WHERE "EmployeeID" = 12').n == "Hugo Dubois"

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
        titles[0] == "count the rows after the join: Jonas's orders become lines"
        titles[1] == "diagnose: an INNER JOIN after a LEFT JOIN drops categories"
        titles[2] == "predict: LEFT all the way looks repaired"
        koanQueries(titles[8])*.trim() == ["___"]
        koanQueries(titles[9])*.trim() == ["___"]
        koansSource().contains("extends NorthwindCoKoanBase")
    }

    def "the video's editor mock quotes koan 1 verbatim, on the line its caret names"() {
        // ED_CODE, ED_CARET ("Ln : 91   Col : 39") and animFill hard-code koan 1's blank line.
        given:
        def lines = koansSource().split("\n")

        expect:
        koanBody("count the rows after the join: Jonas's orders become lines").contains('JOIN "Order Details" d ON ___')
        lines[90].indexOf("___") == 38
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static String script(String name) {
        new File("../courses/learnsql/series2-intermediate/15-multi-table-joins-duplicate-rows/scripts/${name}.sql").text
    }

    private static String koansSource() {
        new File("src/koans/groovy/datazeus/learnsql/series2/_15/MultiTableJoinsDuplicateRowsKoans.groovy").text
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
            "count the rows after the join: Jonas's orders become lines"            : ['d."OrderID" = o."OrderID"'],
            "diagnose: an INNER JOIN after a LEFT JOIN drops categories"            : ["o.\"OrderDate\" >= DATE '2024-12-30' AND o.\"OrderDate\" < DATE '2024-12-31'"],
            "predict: LEFT all the way looks repaired"                              : ["LEFT"],
            "predict: what one more join does to freight per country"               : ['JOIN "Order Details" d ON d."OrderID" = o."OrderID"'],
            "count what you mean: orders, not lines, per year"                      : ['count(DISTINCT o."OrderID")'],
            "SUM(DISTINCT) is wrong: two orders with the same freight"              : ["DISTINCT"],
            "aggregate first: freight and lines per country"                        : ['d."OrderID" = o."OrderID"'],
            "through the bridge: lines and customers for Smoked Fudge"              : ['o."CustomerID"'],
            "write the whole query: orders, freight and sales per year"             : ['''
                SELECT f."Year", f."Orders", f."Freight", s."Sales"
                FROM (SELECT EXTRACT(YEAR FROM "OrderDate") AS "Year",
                             count(*) AS "Orders", SUM("Freight") AS "Freight"
                      FROM "Orders"
                      GROUP BY EXTRACT(YEAR FROM "OrderDate")) AS f
                JOIN (SELECT EXTRACT(YEAR FROM o."OrderDate") AS "Year",
                             ROUND(SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")), 2) AS "Sales"
                      FROM "Orders" o JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                      GROUP BY EXTRACT(YEAR FROM o."OrderDate")) AS s
                  ON s."Year" = f."Year"
                ORDER BY f."Year"
            '''],
            "write the whole query: every employee's orders and units in 2024"     : ['''
                SELECT e."EmployeeID", e."FirstName", n."Orders", u."Units"
                FROM "Employees" e
                LEFT JOIN (SELECT "EmployeeID", count(*) AS "Orders"
                           FROM "Orders"
                           WHERE "OrderDate" >= DATE '2024-01-01' AND "OrderDate" < DATE '2025-01-01'
                           GROUP BY "EmployeeID") AS n
                  ON n."EmployeeID" = e."EmployeeID"
                LEFT JOIN (SELECT o."EmployeeID", sum(d."Quantity") AS "Units"
                           FROM "Orders" o
                           JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                           WHERE o."OrderDate" >= DATE '2024-01-01' AND o."OrderDate" < DATE '2025-01-01'
                           GROUP BY o."EmployeeID") AS u
                  ON u."EmployeeID" = e."EmployeeID"
                ORDER BY e."EmployeeID"
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
