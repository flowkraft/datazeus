package datazeus.learnsql.series2._05

import datazeus.support.NorthwindCoGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 2 · lesson _05
 * "CASE — SQL's If/Then for Labels & Buckets".
 *
 * THE DATA IS NORTHWIND COMPANY S (schema northwind_co_s), like the whole of Series 2 since step C
 * (2026-09-17). Both engines are re-checksummed against `_dataset_info` by NorthwindCoEngines before
 * anything here runs, so every figure below is a figure of THE dataset, not of a lookalike.
 *
 * Every figure the video, the article, the trailer, the short and the koans put in front of a
 * learner is asserted here, on BOTH engines. The lesson's scripts, and the sections that run them:
 *
 *    freight-band-per-order, orders-per-band                        §1 the shape
 *    orders-per-band-whens-swapped                                  §2 the first true WHEN wins
 *    heavy-label-no-else, not-heavy-no-else, not-heavy-with-else    §3 no ELSE means NULL
 *    shipper-names-by-case, shipper-names-by-join,
 *    region-coalesce-and-case                                       §4 the short form, and COALESCE
 *    orders-online-or-offline                                       §5 the hands-on
 *    status-per-channel, status-per-channel-counted-with-count      §6 CASE inside an aggregate
 *    cancelled-share-per-channel-integer-division                   §7 the share: PER ENGINE
 *    cancelled-share-per-channel, cancelled-share-company           §7 the share, portable
 *    shipped-no-ship-date-per-courier-2024                          §8 the case segment
 *
 * §9 asserts every number the KOANS' comments state, and §10 runs the koans file itself, as
 * written, on both engines (ported from Series 1 · 50 §8).
 *
 * ── THE EARN, AS ARITHMETIC ────────────────────────────────────────────────────────────────
 * Freight bands: 2446 light + 4156 medium + 3398 heavy = 10000. Swap the first two WHENs and light
 * is gone: 6602 medium (= 2446 + 4156), the same 3398 heavy. One WHEN and no ELSE: 4383 heavy and
 * 5617 NULL; filtering "not heavy" over that returns 0, and with an ELSE it returns the 5617.
 * The short form written with Series 1's three couriers leaves ShipVia 4's 511 orders NULL; the
 * JOIN names all four.
 *
 * ── THE ONE ASSERTION THAT IS DELIBERATELY NOT "THE SAME ON BOTH ENGINES" (§7) ──────────────
 * SUM(CASE … THEN 1 ELSE 0 END) / count(*) is a whole number divided by a whole number. On
 * PostgreSQL that is INTEGER division — bigint / bigint is a bigint — so every channel's share is
 * 0, which is what CloudBeaver shows and what the video and the article print. On DuckDB `/` is
 * always floating-point division, so Sales rep's share is 61 / 3749 = 0.01627… (a DOUBLE). Both
 * are asserted, each on its own engine, WITH THE TYPE, exactly as Series 1 · 15 asserts NULL
 * placement per engine; then the 100.0 * … version is asserted identical on both. The two
 * near-misses the article names (100 without the .0, and dividing before multiplying) are
 * asserted the same way. THE ENGINE DIFFERENCE HAS NO KOAN: a DuckDB koan would go green for the
 * column CloudBeaver shows as zeros (Series 1 · 20's precedent). The share koans use the portable form.
 *
 * ── THE CASE SEGMENT, AND WHAT THIS GATE DOES NOT CLAIM (§8) ───────────────────────────────
 * The LOCKED figure (.docs/plan-academy-figures-northwind-co-s.md, column C): in 2024, Speedy
 * Express 178 of 1768 orders marked 'Shipped' with no "ShippedDate", 10.1; the other three couriers
 * 0 of 630. The lesson says "marked Shipped, no ship date" and "SQL tells us where to look"; this
 * spec asserts the counts, never a reason for them.
 *
 * ── ORDER, TEXT AND DECIMALS ───────────────────────────────────────────────────────────────
 * Every result the lesson SHOWS is ordered on something that pins it — a unique key, a band's
 * min("Freight"), a count with no ties (asserted), or plain ASCII names that sort the same under
 * DuckDB's byte order and PostgreSQL's collation (asserted by running both) — so pinning its rows is
 * honest. Money is compared BY VALUE via dec(). What each tool PRINTS is the video's concern
 * (decimalAsShownInCloudBeaver, NULL_AS_SHOWN_IN_CLOUDBEAVER), not this gate's.
 */
class CaseExpressionsSpec extends NorthwindCoGateSpec {

    // --- 0. The dataset the whole lesson quotes -----------------------------------------------

    @Unroll
    def "[#engine] the dataset is Northwind Company S, as the lesson quotes it"() {
        given:
        def sql = sqlFor(engine)

        expect: "10000 orders over 2020-2024, four couriers, 120 customers, 80 products"
        sql.firstRow('SELECT count(*) AS n FROM "Orders"').n == 10000
        sql.firstRow('SELECT count(*) AS n FROM "Shippers"').n == 4
        sql.firstRow('SELECT count(*) AS n FROM "Customers"').n == 120
        sql.firstRow('SELECT count(*) AS n FROM "Products"').n == 80
        sql.firstRow('SELECT count(*) AS n FROM "Order Details"').n == 25233
        sql.firstRow('SELECT count(*) AS n FROM "StockMovements"').n == 29264
        sql.firstRow('''SELECT count(*) AS n FROM "Orders"
                        WHERE "OrderDate" < DATE '2020-01-01' OR "OrderDate" >= DATE '2025-01-01' ''').n == 0

        and: "the real Status column the column-per-status report is built on: exactly three values"
        sql.rows('SELECT "Status" AS s, count(*) AS n FROM "Orders" GROUP BY "Status" ORDER BY "Status"')
                .collect { [it.s, it.n] } == [["Cancelled", 175], ["Open", 105], ["Shipped", 9720]]

        and: "and the four channels, no NULL channel"
        sql.rows('SELECT "Channel" AS c FROM "Orders" GROUP BY "Channel"')*.c.toSet() ==
                ["Sales rep", "Web", "Phone", "EDI"] as Set

        where:
        engine << ENGINES
    }

    // --- 1. THE SHAPE -----------------------------------------------------------------------------

    @Unroll
    def "[#engine] the band per order: order 1 on 38.4 is medium, order 5 light, order 2 on 114.85 heavy"() {
        given:
        def rows = sqlFor(engine).rows(script("freight-band-per-order"))

        expect: "band-result's card, row for row"
        rows.collect { [it.OrderID, dec(it.Freight), it.Band] } ==
                [[1, dec("38.4"), "medium"], [2, dec("114.85"), "heavy"], [3, dec("37.25"), "medium"],
                 [4, dec("29.15"), "medium"], [5, dec("18.58"), "light"], [6, dec("18.43"), "light"]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] 2446 light, 4156 medium, 3398 heavy — every one of the 10000 orders gets a band"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-band"))

        expect: "band-counts' card, in min(Freight) order"
        rows.collect { [it.Band, it.Orders] } == [["light", 2446], ["medium", 4156], ["heavy", 3398]]
        rows*.Orders.sum() == 10000

        and: "no freight is NULL, so no order escapes the bands"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "Freight" IS NULL').n == 0

        and: "two orders sit exactly on 25 and one on 50; the words 'under 25' and 'over 50' put them in medium and not-heavy, as the CASE does"
        sqlFor(engine).rows('SELECT "OrderID" AS id FROM "Orders" WHERE "Freight" = 25 ORDER BY "OrderID"')*.id == [650, 1571]
        sqlFor(engine).rows('SELECT "OrderID" AS id FROM "Orders" WHERE "Freight" = 50')*.id == [7133]
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "Freight" = 60').n == 0

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the trailer's tokens are real freights of orders 1-16, and route as the machine draws"() {
        // intro.tsx TOKENS: twelve of orders 1–16, shuffled; 3 light, 5 medium, 4 heavy; 6 over 50.
        given:
        def freight = sqlFor(engine).rows('SELECT "OrderID" AS id, "Freight" AS f FROM "Orders" WHERE "OrderID" <= 16')
                .collectEntries { [(it.id): dec(it.f)] }
        def tokens = [1: "38.4", 5: "18.58", 2: "114.85", 12: "40", 8: "9.87", 14: "54.24",
                      11: "119.97", 4: "29.15", 6: "18.43", 15: "139.09", 7: "59.05", 16: "117.26"]

        expect:
        tokens.every { id, f -> freight[id] == dec(f) }
        tokens.values().count { dec(it) < 25 } == 3
        tokens.values().count { dec(it) >= 25 && dec(it) < 60 } == 5
        tokens.values().count { dec(it) >= 60 } == 4
        tokens.values().count { dec(it) > 50 } == 6

        where:
        engine << ENGINES
    }

    // --- 2. THE FIRST TRUE WHEN WINS -----------------------------------------------------------------

    @Unroll
    def "[#engine] swap the first two WHENs and light disappears: 6602 medium, 3398 heavy"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-band-whens-swapped"))

        expect: "no-light-at-all's card"
        rows.collect { [it.Band, it.Orders] } == [["medium", 6602], ["heavy", 3398]]

        and: "the 6602 is exactly the old light plus the old medium — nothing was lost, it was relabelled"
        rows[0].Orders == 2446 + 4156

        where:
        engine << ENGINES
    }

    // --- 3. NO ELSE MEANS NULL -----------------------------------------------------------------------

    @Unroll
    def "[#engine] one WHEN and no ELSE: 4383 heavy and 5617 NULL — more than half the orders"() {
        given:
        def rows = sqlFor(engine).rows(script("heavy-label-no-else"))

        expect: "unlabelled-nulls' card, NULL last; the short's evidence card"
        rows.collect { [it.Label, it.Orders] } == [["heavy", 4383], [null, 5617]]
        5617 * 2 > 10000

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] 'not heavy' is 0 without the ELSE and 5617 with it"() {
        expect: "not-heavy-zero and always-else; the short's disputed 0 and its yield"
        sqlFor(engine).firstRow(script("not-heavy-no-else"))["Not heavy"] == 0
        sqlFor(engine).firstRow(script("not-heavy-with-else"))["Not heavy"] == 5617

        where:
        engine << ENGINES
    }

    // --- 4. THE SHORT FORM, AND COALESCE -----------------------------------------------------------

    @Unroll
    def "[#engine] the short form with Series 1's three couriers leaves ShipVia 4's 511 orders NULL; the JOIN names all four"() {
        expect: "unnamed-courier's card, NULL last on both engines"
        sqlFor(engine).rows(script("shipper-names-by-case")).collect { [it.Shipper, it.Orders] } ==
                [["Federal Shipping", 831], ["Speedy Express", 7513], ["United Package", 1145], [null, 511]]

        and: "join-instead's card"
        sqlFor(engine).rows(script("shipper-names-by-join")).collect { [it.Shipper, it.Orders] } ==
                [["Federal Shipping", 831], ["Northern Freight Lines", 511], ["Speedy Express", 7513], ["United Package", 1145]]

        and: "the CASE's three names are the stored names for ids 1-3, and 4 is Northern Freight Lines"
        sqlFor(engine).rows('SELECT "ShipperID", "CompanyName" FROM "Shippers" ORDER BY "ShipperID"')
                .collect { [it.ShipperID, it.CompanyName] } ==
                [[1, "Speedy Express"], [2, "United Package"], [3, "Federal Shipping"], [4, "Northern Freight Lines"]]

        and: "every order has a ShipVia of 1-4, so the NULL row is exactly ShipVia 4"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "ShipVia" NOT IN (1, 2, 3, 4) OR "ShipVia" IS NULL').n == 0
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "ShipVia" = 4').n == 511

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] COALESCE and the CASE it abbreviates give identical columns — Canada has regions, Poland none"() {
        given:
        def shown = sqlFor(engine).rows(script("region-coalesce-and-case"))
        def every = sqlFor(engine).rows('''SELECT COALESCE("Region", '(none)') AS a,
                                                  CASE WHEN "Region" IS NULL THEN '(none)' ELSE "Region" END AS b
                                           FROM "Customers"''')

        expect: "the article's eight rows"
        shown.collect { [it.CompanyName, it.Country, it["With COALESCE"], it["With CASE"]] } ==
                [["Cedar Grocers", "Poland", "(none)", "(none)"], ["Emerald Fine Foods", "Canada", "BC", "BC"],
                 ["Golden Supermarket", "Canada", "BC", "BC"], ["Kestrel Provisions", "Poland", "(none)", "(none)"],
                 ["Pampas Foods", "Canada", "BC", "BC"], ["Tundra Foods", "Canada", "QC", "QC"],
                 ["Upland Trading", "Canada", "QC", "QC"], ["Zenith Food Hall", "Poland", "(none)", "(none)"]]

        and: "identical for all 120 customers"
        every.size() == 120
        every.every { it.a == it.b }

        where:
        engine << ENGINES
    }

    // --- 5. THE HANDS-ON ---------------------------------------------------------------------------

    @Unroll
    def "[#engine] the hands-on: 6234 offline and 3766 online"() {
        expect: "hands-on-label's answer line, most orders first"
        sqlFor(engine).rows(script("orders-online-or-offline")).collect { [it["Channel type"], it.Orders] } ==
                [["offline", 6234], ["online", 3766]]

        where:
        engine << ENGINES
    }

    // --- 6. CASE INSIDE AN AGGREGATE ---------------------------------------------------------------

    private static final List CHANNELS = [["Sales rep", 3749, 3669, 61, 19], ["Web", 2765, 2669, 50, 46],
                                          ["Phone", 2485, 2413, 42, 30], ["EDI", 1001, 969, 22, 10]]

    @Unroll
    def "[#engine] a column per status: Sales rep 3749 orders, 3669 / 61 / 19 — the SUM and the count versions agree"() {
        given:
        def viaSum = sqlFor(engine).rows(script("status-per-channel"))
        def viaCount = sqlFor(engine).rows(script("status-per-channel-counted-with-count"))
        def cells = { r -> [r.Channel, r.Orders as int, r.Shipped as int, r.Cancelled as int, r.Open as int] }

        expect: "channel-result's card — every channel"
        viaSum.collect(cells) == CHANNELS
        viaSum*.Orders.sum() == 10000

        and: "every row's three status columns add back up to its orders (Leo's check)"
        viaSum.every { (it.Shipped as int) + (it.Cancelled as int) + (it.Open as int) == (it.Orders as int) }

        and: "no two channels tie on orders, so ORDER BY \"Orders\" DESC pins the rows"
        viaSum*.Orders.toSet().size() == 4

        and: "count-version: the missing ELSE on purpose gives the same columns"
        viaCount.collect(cells) == CHANNELS

        where:
        engine << ENGINES
    }

    // --- 7. THE SHARE — PER ENGINE, THEN PORTABLE ------------------------------------------------------

    def "postgres: SUM(CASE …) / count(*) is integer division — every channel's share is a whole-number 0"() {
        // THE SLIDE'S WHOLE CLAIM (share-zero) and the article's CloudBeaver table. bigint / bigint is
        // a bigint on PostgreSQL, so the fraction is thrown away. If this ever comes back as a decimal,
        // the video, the article and the flashcard that say "CloudBeaver shows 0" are all wrong.
        given:
        def rows = sqlFor("postgres").rows(script("cancelled-share-per-channel-integer-division"))

        expect: "the same four channels and counts as the report before it"
        rows.collect { [it.Channel, it.Orders as int, it.Cancelled as int] } == CHANNELS.collect { [it[0], it[1], it[3]] }

        and: "every share is 0, and it is a WHOLE-NUMBER type — not a decimal that happens to round to 0"
        rows.every { it.Share == 0 }
        rows.every { r -> [Integer, Long, BigInteger].any { t -> t.isInstance(r.Share) } }
    }

    def "duckdb: the same query keeps the fraction — Sales rep 0.016…, as a DOUBLE"() {
        // The Callout's and share-zero's "DuckDB keeps the fraction: 0.016 and a bit for Sales rep".
        // DuckDB's `/` is always floating-point division (`//` is its integer division), so the koans'
        // engine would pass a query CloudBeaver shows as zeros — which is why this rule has no koan.
        given:
        def rows = sqlFor("duckdb").rows(script("cancelled-share-per-channel-integer-division"))

        expect:
        rows.collect { [it.Channel, it.Orders as int, it.Cancelled as int] } == CHANNELS.collect { [it[0], it[1], it[3]] }
        rows.every { it.Share instanceof Double }
        rows[0].Channel == "Sales rep"
        Math.abs((rows[0].Share as double) - 0.01627) < 0.00001
        (rows[0].Share as double) > 0.016 && (rows[0].Share as double) < 0.017

        and: "every share is exactly its count divided by its orders"
        rows.every { r -> Math.abs((r.Share as double) - (r.Cancelled as double) / (r.Orders as double)) < 1e-12 }
    }

    @Unroll
    def "[#engine] 100.0 * … / count(*), rounded to 1: the same shares on both engines — the most cancellations, the lowest share"() {
        given:
        def rows = sqlFor(engine).rows(script("cancelled-share-per-channel"))

        expect: "share-result's card and the article's table"
        rows.collect { [it.Channel, it.Orders as int, it.Cancelled as int, dec(it["Cancelled %"])] } ==
                [["Sales rep", 3749, 61, dec("1.6")], ["Web", 2765, 50, dec("1.8")],
                 ["Phone", 2485, 42, dec("1.7")], ["EDI", 1001, 22, dec("2.2")]]

        and: "the count and the share point at opposite ends: Sales rep most and lowest, EDI fewest and highest"
        rows.max { it.Cancelled as int }.Channel == "Sales rep"
        rows.min { dec(it["Cancelled %"]) }.Channel == "Sales rep"
        rows.min { it.Cancelled as int }.Channel == "EDI"
        rows.max { dec(it["Cancelled %"]) }.Channel == "EDI"

        and: "no share sits on a rounding half, so ROUND agrees on both engines for a reason, not by luck"
        rows.every { r -> notOnHalf(r.Cancelled as long, r.Orders as long) }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the company: 10000 orders, 175 cancelled, 1.8 — and EDI's 2.2 is four orders from it"() {
        given:
        def company = sqlFor(engine).firstRow(script("cancelled-share-company"))

        expect: "company-share's one row"
        (company.Orders as int) == 10000
        (company.Cancelled as int) == 175
        dec(company["Cancelled %"]) == dec("1.8")

        and: "EDI with four fewer cancellations (18 of 1001) would read 1.8 — the company's share"
        dec(sqlFor(engine).firstRow('SELECT ROUND(100.0 * 18 / 1001, 1) AS s').s) == dec("1.8")
        dec(sqlFor(engine).firstRow('SELECT ROUND(100.0 * 19 / 1001, 1) AS s').s) == dec("1.9")

        where:
        engine << ENGINES
    }

    def "the article's two near-misses: 100 without the .0, and dividing before multiplying — per engine"() {
        // "Writing 100 * SUM(…) / count(*) without the .0 gives PostgreSQL 1 for Sales rep instead of
        // 1.6, and SUM(…) / count(*) * 100.0 divides first and gives 0 again." Neither is a script
        // (neither is shown as a query), so they are asserted inline, on Sales rep, on both engines.
        given:
        def noPoint = '''SELECT ROUND(100 * SUM(CASE WHEN "Status" = 'Cancelled' THEN 1 ELSE 0 END) / count(*), 1) AS s
                         FROM "Orders" WHERE "Channel" = 'Sales rep' '''
        def divideFirst = '''SELECT ROUND(SUM(CASE WHEN "Status" = 'Cancelled' THEN 1 ELSE 0 END) / count(*) * 100.0, 1) AS s
                             FROM "Orders" WHERE "Channel" = 'Sales rep' '''

        expect: "PostgreSQL: 1 and 0"
        dec(sqlFor("postgres").firstRow(noPoint).s) == dec("1")
        dec(sqlFor("postgres").firstRow(divideFirst).s) == dec("0")

        and: "DuckDB forgives both, which is the whole danger of trying it there first"
        dec(sqlFor("duckdb").firstRow(noPoint).s) == dec("1.6")
        dec(sqlFor("duckdb").firstRow(divideFirst).s) == dec("1.6")
    }

    // --- 8. THE CASE SEGMENT ----------------------------------------------------------------------

    @Unroll
    def "[#engine] the case: in 2024, Speedy Express 178 of 1768 marked Shipped with no ship date (10.1); the other couriers 0 of 630"() {
        given:
        def rows = sqlFor(engine).rows(script("shipped-no-ship-date-per-courier-2024"))

        expect: "case-result's card, the LOCKED figure (figures sheet, column C)"
        rows.collect { [it.Courier, it.Orders as int, it["Shipped, no date"] as int, dec(it["Share %"])] } ==
                [["Federal Shipping", 216, 0, dec("0")], ["Northern Freight Lines", 125, 0, dec("0")],
                 ["Speedy Express", 1768, 178, dec("10.1")], ["United Package", 289, 0, dec("0")]]

        and: "the other three couriers hold 630 orders in 2024, and every 2024 order is on the card"
        rows.findAll { it.Courier != "Speedy Express" }.collect { it.Orders as int }.sum() == 630
        rows.collect { it.Orders as int }.sum() ==
                sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                           WHERE "OrderDate" >= DATE '2024-01-01' AND "OrderDate" < DATE '2025-01-01' ''').n

        and: "both tests are needed: Cancelled and Open orders never have a ship date either"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "Status" IN ('Cancelled', 'Open') AND "ShippedDate" IS NOT NULL''').n == 0

        where:
        engine << ENGINES
    }

    // --- 9. What the KOANS stand on --------------------------------------------------------------

    @Unroll
    def "[#engine] the koan comments' facts are true"() {
        given:
        def sql = sqlFor(engine)

        expect: "the schema note: 80 products, ten per category, six discontinued, none priced exactly 15 or 30"
        sql.firstRow('SELECT count(*) AS n FROM "Products"').n == 80
        sql.rows('SELECT count(*) AS n FROM "Products" GROUP BY "CategoryID"')*.n.every { it == 10 }
        sql.firstRow('SELECT count(*) AS n FROM "Products" WHERE "Discontinued" = TRUE').n == 6
        sql.firstRow('SELECT count(*) AS n FROM "Products" WHERE "UnitPrice" IN (15, 30)').n == 0
        sql.firstRow('SELECT count(*) AS n FROM "Products" WHERE "UnitsInStock" IS NULL OR "ReorderLevel" IS NULL OR "Discontinued" IS NULL').n == 0

        and: "koan 2: with the stock test first, the answer is 5 discontinued and 4 reorder"
        sql.rows('''SELECT CASE
                             WHEN "UnitsInStock" <= "ReorderLevel" THEN 'reorder'
                             WHEN "Discontinued" = TRUE THEN 'discontinued'
                             ELSE 'ok'
                           END AS "Stock", count(*) AS "Products"
                    FROM "Products" GROUP BY "Stock" ORDER BY "Stock"''')
                .collect { [it.Stock, it.Products] } == [["discontinued", 5], ["ok", 71], ["reorder", 4]]

        and: "koan 3 and 4: 25233 lines; without an ELSE the not-discounted count would be 0"
        sql.firstRow('SELECT count(*) AS n FROM "Order Details"').n == 25233
        sql.firstRow('''SELECT count(*) AS n
                        FROM (SELECT CASE WHEN "Discount" > 0 THEN 'discounted' END AS "Label"
                              FROM "Order Details") AS t
                        WHERE "Label" <> 'discounted' ''').n == 0

        and: "koans 7 and 9: the discount is only ever 0, 0.05, 0.1 or 0.15, so '= 0' and '> 0' split every line"
        sql.rows('SELECT DISTINCT "Discount" AS d FROM "Order Details" ORDER BY d')
                .collect { dec(it.d) } == [dec("0"), dec("0.05"), dec("0.1"), dec("0.15")]

        and: "koan 5: eleven orders on 2024-12-31, three to Portland in OR, and no other ship region among them"
        sql.firstRow('''SELECT count(*) AS n FROM "Orders" WHERE "OrderDate" = DATE '2024-12-31' ''').n == 11
        sql.rows('''SELECT DISTINCT "ShipRegion" AS r FROM "Orders"
                    WHERE "OrderDate" = DATE '2024-12-31' AND "ShipRegion" IS NOT NULL''')*.r == ["OR"]

        and: "koan 7: five order years, 2020 to 2024"
        sql.rows('SELECT DISTINCT EXTRACT(YEAR FROM "OrderDate") AS y FROM "Orders" ORDER BY y').collect { dec(it.y) } ==
                (2020..2024).collect { dec(it) }

        and: "koan 8: the top three products by lines do not tie with the fourth — LIMIT 3 cuts no tie"
        def lineCounts = sql.rows('''SELECT count(*) AS n FROM "Order Details"
                                     GROUP BY "ProductID" ORDER BY n DESC LIMIT 4''')*.n
        lineCounts.take(3) == [1252, 1160, 1156]
        lineCounts[3] < 1156

        and: "koan 9: the eight categories hold all 25233 lines, and no share sits on a rounding half"
        def cat = sql.rows('''SELECT c."CategoryName" AS name, count(*) AS n,
                                     SUM(CASE WHEN d."Discount" > 0 THEN 1 ELSE 0 END) AS k
                              FROM "Order Details" d
                              JOIN "Products" p ON p."ProductID" = d."ProductID"
                              JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
                              GROUP BY c."CategoryName" ORDER BY c."CategoryName"''')
        cat.find { it.name == "Beverages" }.with { [it.n as int, it.k as int] } == [2686, 1319]
        cat.size() == 8
        cat.collect { it.n as int }.sum() == 25233
        cat.every { r -> notOnHalf(r.k as long, r.n as long) }

        and: "koan 11: the ledger holds four movement types, so count(*) is NOT the number of sales — the comment's warning is real"
        sql.rows('SELECT DISTINCT "MovementType" AS t FROM "StockMovements"')*.t.toSet() == ["Receipt", "Sale", "Return", "Adjustment"] as Set
        sql.firstRow('''SELECT count(*) AS n FROM "StockMovements" WHERE "MovementType" IN ('Receipt', 'Adjustment')''').n > 0

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

        and: "the koans run on Northwind Company"
        koansSource().contains("extends NorthwindCoKoanBase")

        and: "the share koans are written the portable way — 100.0, with the point"
        ANSWERS["a share of the lines: 100.0 before you divide"][0].startsWith("100.0 * ")
        ANSWERS["write the whole query: the return rate of each category"][0].contains("100.0 * SUM(")
    }

    def "the video's editor mock quotes koan 1 verbatim"() {
        // ED_CODE and animFill in the video file hard-code koan 1's blank line and its expected rows;
        // if the koan moves, the mock would draw a line the file no longer has.
        expect:
        koanBody("a label for every product: finish the CASE").contains("___ 'premium'")
        koanBody("a label for every product: finish the CASE").contains('shouldReturn([["budget", 28], ["standard", 27], ["premium", 25]]')
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
            "the first true WHEN wins: write the tests in the right order"   : ["WHEN \"Discontinued\" = TRUE THEN 'discontinued' WHEN \"UnitsInStock\" <= \"ReorderLevel\" THEN 'reorder'"],
            "no ELSE means NULL: count only what the CASE labelled"          : ['"Label"'],
            "a NULL fails not-equal too: give the rest a label"              : ["ELSE 'full price'"],
            "COALESCE is a CASE: write it the long way"                      : ['ELSE "ShipRegion"'],
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
            "write the whole query: the return rate of each category"        : ['''
                SELECT c."CategoryName",
                       SUM(CASE WHEN m."MovementType" = 'Sale' THEN 1 ELSE 0 END) AS "Sales",
                       SUM(CASE WHEN m."MovementType" = 'Return' THEN 1 ELSE 0 END) AS "Returns",
                       ROUND(100.0 * SUM(CASE WHEN m."MovementType" = 'Return' THEN 1 ELSE 0 END)
                             / SUM(CASE WHEN m."MovementType" = 'Sale' THEN 1 ELSE 0 END), 1) AS "Return %"
                FROM "StockMovements" m
                JOIN "Products" p ON p."ProductID" = m."ProductID"
                JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
                GROUP BY c."CategoryName"
                ORDER BY c."CategoryName"
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

    /** True when 100 * part / whole, rounded to ONE decimal place, is not an exact half at the
     *  second decimal (1000 * part / whole = k + 0.5), where engines may round differently. */
    private static boolean notOnHalf(long part, long whole) {
        long twice = 2000L * part
        twice % whole != 0 || (twice.intdiv(whole) as long) % 2 == 0
    }
}
