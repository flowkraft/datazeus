package datazeus.learnsql.series2._10

import datazeus.support.NorthwindCoGateSpec
import spock.lang.Unroll

import java.sql.SQLException

/**
 * VERIFIED spec = the PUBLISH GATE for Series 2 · lesson _10
 * "Dates & Times — Getting Ranges and Boundaries Right", on NORTHWIND COMPANY S (schema northwind_co_s).
 *
 * Every figure the video, the article, the trailer, the short and the koans put in front of a
 * learner is asserted here, on BOTH engines, plus the errors the video draws. The lesson's scripts,
 * and the sections that run them:
 *
 *    orders-per-month-extract, orders-per-month, orders-per-week               §1 truncate, don't extract
 *    shipped-in-november-between, shipped-in-december-between,
 *    shipped-in-november-half-open, keyed-in-november, orders-in-may-2024      §2 half-open ranges
 *    last-30-days, last-30-days-of-data                                         §3 anchor on the data
 *    slow-orders-interval-error, slow-orders, days-to-ship,
 *    average-days-to-ship, late-orders                                          §4 date arithmetic
 *    orders-per-month-one-customer, customer-month-spine,
 *    customer-spine-count-star                                                  §5 the months with no rows
 *    no-ship-date-per-week                                                      §6 the case: since when?
 *
 * §7 asserts every number the KOANS' comments state, and §8 runs the koans file itself, as
 * written, on both engines (ported from Series 1 · 50 §8).
 *
 * 2026-09-17, STEP C (plan-academy-course-stories-artefacts.md §10): the whole episode moved from the
 * frozen Northwind (79 orders, Dec 2022 – Jun 2024) to Northwind Company S (10,000 orders,
 * 2020-01-01 – 2024-12-31). Every example was re-found on the new data; the superseded case file
 * ("since when", per quarter, days waiting, past the required date) is replaced by the story map's
 * S2·10 segment: 'Shipped' orders with no ship date, week by week over 2024 (figures sheet, column C).
 *
 * ── DATES, AS VALUES ───────────────────────────────────────────────────────────────────────
 * date_trunc hands back a TIMESTAMP on PostgreSQL and a DATE on DuckDB; the drivers hand back
 * Timestamp, LocalDate or LocalDateTime. The CALENDAR VALUE is what the lesson claims, so dates are
 * compared through day(): the first ten characters, YYYY-MM-DD. How CloudBeaver PRINTS them
 * (2024-12-01 00:00:00.000) is the video's concern, not this gate's.
 *
 * ── CURRENT_DATE ───────────────────────────────────────────────────────────────────────────
 * last-30-days returns 0 on every day after 2025-01-30, which is every day this lesson can be run.
 * It is asserted as 0 together with the fact that makes it so: the last order is 2024-12-31.
 */
class DatesAndTimesSpec extends NorthwindCoGateSpec {

    // --- 0. The dataset's own boundaries --------------------------------------------------------

    @Unroll
    def "[#engine] orders run 2020-01-01 to 2024-12-31, shipments to 2024-12-29; the order dates are midnight and CreatedAt is not"() {
        given:
        def r = sqlFor(engine).firstRow('''SELECT min("OrderDate") AS o1, max("OrderDate") AS o2,
                                                  min("ShippedDate") AS s1, max("ShippedDate") AS s2,
                                                  count(*) AS n FROM "Orders"''')

        expect:
        day(r.o1) == "2020-01-01"
        day(r.o2) == "2024-12-31"
        day(r.s1) == "2020-01-04"
        day(r.s2) == "2024-12-29"
        r.n == 10000

        and: "the article's 'every order, required and ship date is midnight'"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE CAST("OrderDate" AS TIME) <> TIME '00:00:00'
                                      OR CAST("RequiredDate" AS TIME) <> TIME '00:00:00'
                                      OR CAST("ShippedDate" AS TIME) <> TIME '00:00:00' ''').n == 0

        and: "…and 'CreatedAt, when the order was keyed in, carries the time of day': never midnight, 08:00 to 17:59"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders" WHERE CAST("CreatedAt" AS TIME) = TIME '00:00:00' ''').n == 0
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE CAST("CreatedAt" AS TIME) < TIME '08:00:00' OR CAST("CreatedAt" AS TIME) >= TIME '18:00:00' ''').n == 0

        and: "every month of the five years has orders, so EXTRACT's twelve bars each hold five months"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM (SELECT date_trunc('month', "OrderDate") AS m FROM "Orders" GROUP BY date_trunc('month', "OrderDate")) AS t''').n == 60

        where:
        engine << ENGINES
    }

    // --- 1. TRUNCATE, DON'T EXTRACT ----------------------------------------------------------------

    @Unroll
    def "[#engine] EXTRACT folds five years: June 739 is five Junes (120, 130, 144, 168, 177); no real month ever passed 277"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-month-extract"))

        expect: "five-junes' card, row for row"
        rows.collect { [dec(it.Month), dec(it.Orders)] } ==
                [[1, 805], [2, 809], [3, 825], [4, 786], [5, 774], [6, 739], [7, 828], [8, 653], [9, 751], [10, 821], [11, 1104], [12, 1105]]
                        .collect { [dec(it[0]), dec(it[1])] }
        rows.sum { it.Orders as int } == 10000

        and: "the trailer's and the short's split: the five Junes"
        (2020..2024).collect { y ->
            sqlFor(engine).firstRow("""SELECT count(*) AS n FROM "Orders"
                                       WHERE "OrderDate" >= DATE '${y}-06-01' AND "OrderDate" < DATE '${y}-07-01'""".toString()).n as int
        } == [120, 130, 144, 168, 177]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] date_trunc keeps the year: 60 months, 90 to 277 orders each"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-month"))

        expect: "sixty-months' card: the first three and the last four"
        rows.size() == 60
        rows.take(3).collect { [day(it.Month), it.Orders as int] } ==
                [["2020-01-01", 138], ["2020-02-01", 128], ["2020-03-01", 126]]
        rows.takeRight(4).collect { [day(it.Month), it.Orders as int] } ==
                [["2024-09-01", 168], ["2024-10-01", 205], ["2024-11-01", 277], ["2024-12-01", 254]]

        and: "the smallest month (August 2020, 90) and the largest (November 2024, 277)"
        cellsOf(rows.min { it.Orders as int }) { r -> [day(r.Month), r.Orders as int] } == ["2020-08-01", 90]
        cellsOf(rows.max { it.Orders as int }) { r -> [day(r.Month), r.Orders as int] } == ["2024-11-01", 277]

        and: "the trailer's tearing pad, all sixty counts in order"
        rows*.Orders.collect { it as int } == TRAILER_MONTHS

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] by week: 262 weeks; the first is labelled 2019-12-30 and has five days, the last has two"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-week"))
        def cells = rows.collect { [day(it.Week), it.Orders as int, day(it["First order"]), day(it["Last order"])] }

        expect: "partial-weeks' card: the first two weeks and the last two"
        rows.size() == 262
        cells.take(2) == [["2019-12-30", 21, "2020-01-01", "2020-01-05"], ["2020-01-06", 35, "2020-01-06", "2020-01-12"]]
        cells.takeRight(2) == [["2024-12-23", 60, "2024-12-23", "2024-12-29"], ["2024-12-30", 20, "2024-12-30", "2024-12-31"]]

        and: "weeks start on Monday on both engines: 2019-12-30 and 2024-12-30 are Mondays, and 2020-01-01 a Wednesday"
        cellsOf(sqlFor(engine).firstRow('''SELECT EXTRACT(ISODOW FROM DATE '2019-12-30') AS a, EXTRACT(ISODOW FROM DATE '2024-12-30') AS b,
                                          EXTRACT(ISODOW FROM DATE '2020-01-01') AS c''')) { r -> [dec(r.a), dec(r.b), dec(r.c)] } == [1, 1, 3].collect { dec(it) }

        and: "every week label is a Monday, and no week between the first and the last is missing"
        sqlFor(engine).rows('''SELECT DISTINCT EXTRACT(ISODOW FROM date_trunc('week', "OrderDate")) AS d FROM "Orders"''')
                .collect { dec(it.d) } == [dec(1)]
        (java.time.LocalDate.parse(cells.last()[0]).toEpochDay() - java.time.LocalDate.parse(cells.first()[0]).toEpochDay()) / 7 + 1 == 262

        where:
        engine << ENGINES
    }

    // --- 2. HALF-OPEN RANGES -----------------------------------------------------------------------

    @Unroll
    def "[#engine] BETWEEN puts the 14 shipments of 1 December in November (277) and in December (236); half-open November is 263"() {
        given:
        def nov = sqlFor(engine).firstRow(script("shipped-in-november-between"))
        def dec = sqlFor(engine).firstRow(script("shipped-in-december-between"))
        def halfOpen = sqlFor(engine).firstRow(script("shipped-in-november-half-open"))

        expect: "december-first's card"
        [nov.Shipments as int, day(nov["Last shipped"])] == [277, "2024-12-01"]

        and: "counted-twice's card"
        [dec.Shipments as int, day(dec["First shipped"])] == [236, "2024-12-01"]

        and: "half-open's card"
        [halfOpen.Shipments as int, day(halfOpen["Last shipped"])] == [263, "2024-11-30"]

        and: "the difference is exactly the day both BETWEENs share: 14 shipped on 2024-12-01"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders" WHERE "ShippedDate" = DATE '2024-12-01' ''').n == 14

        and: "November and December half-open are 499 shipments; BETWEEN's 277 + 236 = 513 describes 499"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" >= DATE '2024-11-01' AND "ShippedDate" < DATE '2025-01-01' ''').n == 499

        and: "on the midnight ship dates, BETWEEN ending on the 30th happens to agree with half-open (263)"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" BETWEEN DATE '2024-11-01' AND DATE '2024-11-30' ''').n == 263

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] on CreatedAt, which has a time of day, BETWEEN … the 30th drops the 7 orders keyed in on 30 November: 271 against 278"() {
        given:
        def r = sqlFor(engine).firstRow(script("keyed-in-november"))

        expect: "time-of-day's card (SUM is BIGINT on PostgreSQL, HUGEINT on DuckDB)"
        (r["Ends on the 30th"] as int) == 271
        (r["Half-open"] as int) == 278

        and: "the 7 are exactly the orders keyed in during 30 November"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "CreatedAt" >= DATE '2024-11-30' AND "CreatedAt" < DATE '2024-12-01' ''').n == 7

        and: "a timestamp compared with a DATE is compared with midnight at the start of that day"
        sqlFor(engine).firstRow('''SELECT TIMESTAMP '2024-11-30 14:00:00' BETWEEN DATE '2024-11-01' AND DATE '2024-11-30' AS b''').b == false

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the hands-on: 181 orders placed in May 2024"() {
        expect:
        sqlFor(engine).firstRow(script("orders-in-may-2024")).Orders == 181

        where:
        engine << ENGINES
    }

    // --- 3. ANCHOR ON THE DATA ---------------------------------------------------------------------

    @Unroll
    def "[#engine] the last 30 days of today: 0 — the last 30 days of the data: 254 orders, 1 to 31 December 2024"() {
        given:
        def anchored = sqlFor(engine).firstRow(script("last-30-days-of-data"))

        expect: "last-30-days' 0"
        sqlFor(engine).firstRow(script("last-30-days")).Orders == 0

        and: "the anchor: the last order minus 30 days is midnight on 2024-12-01"
        day(sqlFor(engine).firstRow('''SELECT (SELECT max("OrderDate") FROM "Orders") - INTERVAL '30 days' AS d''').d) == "2024-12-01"

        and: "anchor-result's card"
        [anchored.Orders as int, day(anchored.From), day(anchored.To)] == [254, "2024-12-01", "2024-12-31"]

        where:
        engine << ENGINES
    }

    // --- 4. DATE ARITHMETIC ------------------------------------------------------------------------

    def "PostgreSQL refuses an interval compared with a number, with the exact error the video draws"() {
        when:
        sqlFor("postgres").rows(script("slow-orders-interval-error"))

        then:
        SQLException e = thrown()
        e.SQLState == "42883"
        e.message.contains("operator does not exist: interval > integer")
        e.message.contains("Position: 82")
    }

    def "DuckDB refuses it too, in its own words"() {
        when:
        sqlFor("duckdb").rows(script("slow-orders-interval-error"))

        then:
        SQLException e = thrown()
        e.message.contains("Cannot compare values of type INTERVAL")
    }

    def "the article's aside: PostgreSQL averages an INTERVAL, the DuckDB the koans run on refuses"() {
        when:
        def pg = sqlFor("postgres").firstRow('SELECT AVG("ShippedDate" - "OrderDate") AS a FROM "Orders"').a

        then:
        pg.toString().contains("4 days 19 hours")

        when:
        sqlFor("duckdb").rows('SELECT AVG("ShippedDate" - "OrderDate") AS a FROM "Orders"')

        then:
        SQLException e = thrown()
        e.message.contains("avg(INTERVAL)")
    }

    @Unroll
    def "[#engine] cast to DATE, then subtract: 193 slow orders, orders 1–5 took 4 3 4 3 6 days, average 4.8, longest 24, three late in December"() {
        given:
        def firstFive = sqlFor(engine).rows(script("days-to-ship"))
        def avg = sqlFor(engine).firstRow(script("average-days-to-ship"))
        def late = sqlFor(engine).rows(script("late-orders"))

        expect: "slow-orders' 193"
        sqlFor(engine).firstRow(script("slow-orders"))["Slow orders"] == 193

        and: "the same 193 with the interval compared with an interval — the cast changed the type, not the answer"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" - "OrderDate" > INTERVAL '7 days' ''').n == 193

        and: "the article's days-to-ship"
        firstFive.collect { [it.OrderID, day(it.OrderDate), day(it.ShippedDate), it.Days as int] } ==
                [[1, "2020-01-01", "2020-01-05", 4], [2, "2020-01-01", "2020-01-04", 3], [3, "2020-01-01", "2020-01-05", 4],
                 [4, "2020-01-01", "2020-01-04", 3], [5, "2020-01-01", "2020-01-07", 6]]

        and: "the article's average and longest, and the 468 orders with no ship date AVG skips"
        dec(avg["Average days"]) == dec("4.8")
        (avg.Longest as int) == 24
        sqlFor(engine).firstRow('SELECT count(*) - count("ShippedDate") AS n FROM "Orders"').n == 468

        and: "the article's 'a normal shipment takes 2 to 7 days; the slow ones 15 to 24'"
        sqlFor(engine).rows('''SELECT DISTINCT CAST("ShippedDate" AS DATE) - CAST("OrderDate" AS DATE) AS d FROM "Orders"
                               WHERE "ShippedDate" IS NOT NULL ORDER BY d''').collect { it.d as int } == (2..7) + (15..24)

        and: "late-orders' card: December 2024, ordered by ship date and then order id (9696 and 9725 share the 20th)"
        late.collect { [it.OrderID, day(it.RequiredDate), day(it.ShippedDate)] } ==
                [[9720, "2024-12-12", "2024-12-15"], [9696, "2024-12-10", "2024-12-20"], [9725, "2024-12-12", "2024-12-20"]]

        and: "in this data every required date is 14 days after the order, so the slow 193 are also the late 193"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders" WHERE CAST("RequiredDate" AS DATE) - CAST("OrderDate" AS DATE) <> 14''').n == 0
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "ShippedDate" > "RequiredDate"').n == 193

        where:
        engine << ENGINES
    }

    // --- 5. THE MONTHS WITH NO ROWS ------------------------------------------------------------------

    @Unroll
    def "[#engine] Bramble Supermarket in 2024: GROUP BY 4 rows; the spine 12 months, 8 zeros; count(*) 14 against 6"() {
        given:
        def grouped = sqlFor(engine).rows(script("orders-per-month-one-customer"))
        def spine = sqlFor(engine).rows(script("customer-month-spine"))
        def totals = sqlFor(engine).firstRow(script("customer-spine-count-star"))

        expect: "BRAM3 is Bramble Supermarket, in Oslo"
        cellsOf(sqlFor(engine).firstRow('''SELECT "CompanyName" AS n, "City" AS c FROM "Customers" WHERE "CustomerID" = 'BRAM3' ''')) { r -> [r.n, r.c] } == ["Bramble Supermarket", "Oslo"]

        and: "missing-months' card"
        grouped.collect { [day(it.Month), it.Orders as int] } ==
                [["2024-01-01", 2], ["2024-05-01", 1], ["2024-10-01", 1], ["2024-11-01", 2]]

        and: "spine-result's card: twelve months in order, 8 of them zero, 6 orders in all"
        spine.collect { [day(it.Month), it.Orders as int] } == [
                ["2024-01-01", 2], ["2024-02-01", 0], ["2024-03-01", 0], ["2024-04-01", 0], ["2024-05-01", 1], ["2024-06-01", 0],
                ["2024-07-01", 0], ["2024-08-01", 0], ["2024-09-01", 0], ["2024-10-01", 1], ["2024-11-01", 2], ["2024-12-01", 0]]
        spine.count { (it.Orders as int) == 0 } == 8

        and: "count-star-trap's card: 6 order rows plus 8 empty months = 14"
        (totals["count(*)"] as int) == 14
        (totals["count(OrderID)"] as int) == 6

        and: "the customer's condition belongs in the ON: moved to a WHERE, the empty months are gone again (the lesson on JOINs)"
        sqlFor(engine).rows('''SELECT s.month AS m, count(o."OrderID") AS n
                               FROM generate_series(TIMESTAMP '2024-01-01', TIMESTAMP '2024-12-01', INTERVAL '1 month') AS s(month)
                               LEFT JOIN "Orders" o ON date_trunc('month', o."OrderDate") = s.month
                               WHERE o."CustomerID" = 'BRAM3'
                               GROUP BY s.month''').size() == 4

        and: "none of Bramble's 2024 orders is part of the case: every one shipped with a ship date"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders" WHERE "CustomerID" = 'BRAM3'
                                   AND "OrderDate" >= DATE '2024-01-01' AND ("ShippedDate" IS NULL OR "Status" <> 'Shipped')''').n == 0

        where:
        engine << ENGINES
    }

    // --- 6. THE CASE: SINCE WHEN? -----------------------------------------------------------------
    // The story map's S2·10 segment (figures sheet, column C): a steady 3–12 a week from the week of
    // 2024-03-04 to the week of 2024-08-26; 0–1 a week before and after.

    @Unroll
    def "[#engine] 'Shipped' with no ship date, per week of 2024: 0 until the week of 4 March, 3 to 12 a week to the week of 26 August, then 0 or 1"() {
        given:
        def rows = sqlFor(engine).rows(script("no-ship-date-per-week"))
        def cells = rows.collect { [day(it.Week), it.Orders as int, it["No ship date"] as int] }
        int start = cells.findIndexOf { it[0] == "2024-03-04" }
        int stop = cells.findIndexOf { it[0] == "2024-08-26" }

        expect: "53 weeks, every one with orders, so the conditional count keeps the zeros"
        rows.size() == 53
        cells.first()[0] == "2024-01-01"
        cells.last()[0..1] == ["2024-12-30", 20]
        cells.every { it[1] > 0 }

        and: "case-weeks' card, row for row, around the start and the stop"
        cells[(start - 2)..(start + 1)] ==
                [["2024-02-19", 50, 0], ["2024-02-26", 52, 0], ["2024-03-04", 44, 8], ["2024-03-11", 53, 8]]
        cells[(stop - 1)..(stop + 2)] ==
                [["2024-08-19", 35, 7], ["2024-08-26", 33, 4], ["2024-09-02", 35, 0], ["2024-09-09", 38, 0]]

        and: "inside the window, 26 weeks, every one 3 to 12"
        stop - start + 1 == 26
        cells[start..stop].collect { it[2] }.min() == 3
        cells[start..stop].collect { it[2] }.max() == 12

        and: "outside it, never more than 1 a week (and 1 only once, the week of 28 October)"
        (cells.take(start) + cells.drop(stop + 1)).every { it[2] <= 1 }
        (cells.take(start) + cells.drop(stop + 1)).findAll { it[2] == 1 }*.getAt(0) == ["2024-10-28"]

        and: "the article's aside: filtered in the WHERE and counted as rows, only the 27 weeks that have some come back"
        sqlFor(engine).rows('''SELECT date_trunc('week', "OrderDate") AS w, count(*) AS n FROM "Orders"
                               WHERE "Status" = 'Shipped' AND "ShippedDate" IS NULL
                                 AND "OrderDate" >= DATE '2024-01-01' AND "OrderDate" < DATE '2025-01-01'
                               GROUP BY date_trunc('week', "OrderDate")''').size() == 27

        and: "178 in all — the CASE lesson's 178 for Speedy Express, and every one of them is Speedy Express's"
        cells.sum { it[2] } == 178
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders" WHERE "Status" = 'Shipped' AND "ShippedDate" IS NULL
                                   AND "OrderDate" >= DATE '2024-01-01' AND "OrderDate" < DATE '2025-01-01' AND "ShipVia" <> 1''').n == 0

        where:
        engine << ENGINES
    }

    // --- 7. What the KOANS stand on --------------------------------------------------------------

    @Unroll
    def "[#engine] the koan comments' facts are true"() {
        expect: "the header: 9532 invoices, 223 unpaid, 2765 web orders, 12 employees, invoice dates 2020-01-04 to 2024-12-29"
        cellsOf(sqlFor(engine).firstRow('SELECT count(*) AS n, count(*) - count("PaidDate") AS u, min("InvoiceDate") AS a, max("InvoiceDate") AS b FROM "Invoices"')) { r -> [r.n as int, r.u as int, day(r.a), day(r.b)] } == [9532, 223, "2020-01-04", "2024-12-29"]
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "WebOrders"').n == 2765
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Employees"').n == 12

        and: "koan 1: EXTRACT(MONTH) would give 12 groups"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM (SELECT EXTRACT(MONTH FROM "InvoiceDate") AS m
                                   FROM "Invoices" GROUP BY EXTRACT(MONTH FROM "InvoiceDate")) AS t''').n == 12

        and: "koan 2: the EXTRACT version returned 839, and the date_trunc version agrees with the half-open answer"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Invoices" WHERE EXTRACT(MONTH FROM "PaidDate") = 3').n == 839
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Invoices" WHERE date_trunc('month', "PaidDate") = DATE '2024-03-01' ''').n == 191

        and: "koan 4: the BETWEEN version returned 100, and the 4 it missed were received during 31 December"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "WebOrders"
                                   WHERE "ReceivedAt" BETWEEN DATE '2024-12-01' AND DATE '2024-12-31' ''').n == 100
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "WebOrders"
                                   WHERE "ReceivedAt" >= DATE '2024-12-31' AND "ReceivedAt" < DATE '2025-01-01' ''').n == 4

        and: "koan 5: CURRENT_DATE would find no invoice in the last 7 days"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Invoices"
                                   WHERE "InvoiceDate" >= CURRENT_DATE - INTERVAL '7 days' ''').n == 0

        and: "koan 9: 13 of January 2020's 31 days had a web order, so a day can hold two"
        sqlFor(engine).firstRow('''SELECT count(DISTINCT date_trunc('day', "ReceivedAt")) AS n FROM "WebOrders"
                                   WHERE "ReceivedAt" >= DATE '2020-01-01' AND "ReceivedAt" < DATE '2020-02-01' ''').n == 13

        and: "koan 10: the oldest unpaid invoice is dated 2024-11-15"
        day(sqlFor(engine).firstRow('SELECT min("InvoiceDate") AS d FROM "Invoices" WHERE "PaidDate" IS NULL').d) == "2024-11-15"

        where:
        engine << ENGINES
    }

    def "koan 8's broken WHERE is refused by both engines, as its comment says"() {
        expect:
        ENGINES.every { engine ->
            try {
                sqlFor(engine).rows('''SELECT count(*) FROM "WebOrders" w JOIN "Orders" o ON o."OrderID" = w."OrderID"
                                       WHERE w."ReceivedAt" - o."OrderDate" > 1''')
                false
            } catch (SQLException ignored) {
                true
            }
        }
    }

    // --- 8. THE KOAN FILE ITSELF, RUN AS WRITTEN (Series 1 · 50 §8) --------------------------------

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
        titles[0] == "date_trunc keeps the year: how many months had an invoice"
        titles[1] == "diagnose: five Marches in one count"
        titles[2] == "a half-open range: invoices issued in the first quarter of 2024"
        koanQueries(titles[8])*.trim() == ["___"]
        koanQueries(titles[9])*.trim() == ["___"]
        koansSource().contains("extends NorthwindCoKoanBase")
    }

    def "the video's editor mock quotes koan 1 verbatim, on the line its caret names"() {
        // ED_CODE, ED_CARET ("Ln : 95   Col : 37") and animFill in the video file hard-code koan 1's
        // blank line; if the koan moves, the mock would draw a line the file no longer has.
        given:
        def lines = koansSource().split("\n")

        expect:
        koanBody("date_trunc keeps the year: how many months had an invoice")
                .contains('FROM (SELECT date_trunc(___, "InvoiceDate") AS "Month"')
        lines[94].indexOf("___") == 36
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** The trailer's sixty month pages (intro.tsx MONTHS), January 2020 to December 2024. */
    private static final List<Integer> TRAILER_MONTHS = [
            138, 128, 126, 129, 135, 120, 153, 90, 130, 134, 179, 176,
            148, 138, 155, 155, 129, 130, 136, 131, 134, 143, 202, 201,
            139, 162, 160, 148, 167, 144, 159, 123, 151, 164, 225, 240,
            191, 199, 180, 159, 162, 168, 185, 138, 168, 175, 221, 234,
            189, 182, 204, 195, 181, 177, 195, 171, 168, 205, 277, 254]

    /** A row turned into a list of cells by `c` — a closure whose owner is the spec, so day() and dec() resolve here. */
    private static List cellsOf(Object row, Closure<List> c) { c.call(row) }

    private static String script(String name) {
        new File("../courses/learnsql/series2-intermediate/10-dates-and-times/scripts/${name}.sql").text
    }

    private static String koansSource() {
        new File("src/koans/groovy/datazeus/learnsql/series2/_10/DatesAndTimesKoans.groovy").text
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
            "date_trunc keeps the year: how many months had an invoice"               : ["'month'"],
            "diagnose: five Marches in one count"                                     : ["\"PaidDate\" >= DATE '2024-03-01' AND \"PaidDate\" < DATE '2024-04-01'"],
            "a half-open range: invoices issued in the first quarter of 2024"         : ["\"InvoiceDate\" < DATE '2024-04-01'"],
            "diagnose: BETWEEN misses the evening of the last day"                    : ["\"ReceivedAt\" >= DATE '2024-12-01' AND \"ReceivedAt\" < DATE '2025-01-01'"],
            "anchor recent on the data: the last week of invoicing"                   : ['(SELECT max("InvoiceDate") FROM "Invoices")'],
            "a date plus an INTERVAL: five years' service when the data starts"       : ["INTERVAL '5 years'"],
            "a date minus a date: the quickest and the slowest payment"               : ['"InvoiceDate"', '"InvoiceDate"'],
            "diagnose: an INTERVAL is not a number"                                   : ['CAST(w."ReceivedAt" AS DATE) - CAST(o."OrderDate" AS DATE) > 1'],
            "write the whole query: every day of January 2020, with its web orders"   : ['''
                SELECT count(DISTINCT s.day) AS "Days",
                       count(w."WebOrderID") AS "Web orders",
                       count(*) - count(w."WebOrderID") AS "Days with none"
                FROM generate_series(TIMESTAMP '2020-01-01', TIMESTAMP '2020-01-31', INTERVAL '1 day') AS s(day)
                LEFT JOIN "WebOrders" w ON date_trunc('day', w."ReceivedAt") = s.day
            '''],
            "write the whole query: the invoices still unpaid, and the oldest of them": ['''
                SELECT count(*) AS "Unpaid",
                       (SELECT max("InvoiceDate") FROM "Invoices") - min("InvoiceDate") AS "Oldest, in days"
                FROM "Invoices"
                WHERE "PaidDate" IS NULL
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

    /** A date, a timestamp or a date-time, as its calendar day: YYYY-MM-DD. */
    private static String day(Object v) { v == null ? null : v.toString().take(10) }

    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }
}
