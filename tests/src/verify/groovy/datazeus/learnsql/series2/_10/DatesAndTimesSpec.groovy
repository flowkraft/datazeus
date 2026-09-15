package datazeus.learnsql.series2._10

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

import java.sql.SQLException

/**
 * VERIFIED spec = the PUBLISH GATE for Series 2 · lesson _10
 * "Dates & Times — Getting Ranges and Boundaries Right".
 *
 * Every figure the video, the article, the short and the koans put in front of a learner is
 * asserted here, on BOTH engines, plus the error the video draws. The lesson's scripts, and the
 * sections that run them:
 *
 *    orders-per-month-extract, orders-per-month, orders-per-quarter            §1 truncate, don't extract
 *    shipped-in-july-between, shipped-in-august-between,
 *    shipped-in-july-half-open, orders-in-may-2024                              §2 half-open ranges
 *    last-30-days, last-30-days-of-data                                         §3 anchor on the data
 *    slow-orders-interval-error, slow-orders, days-to-ship,
 *    average-days-to-ship, late-orders                                          §4 date arithmetic
 *    orders-per-day-june, june-date-spine, june-spine-count-star                §5 the missing days
 *    open-orders-per-quarter, open-orders-days-waiting,
 *    open-orders-past-required-date                                             §6 the case file: since when?
 *
 * §7 asserts every number the KOANS' comments state, and §8 runs the koans file itself, as
 * written, on both engines (ported from Series 1 · 50 §8).
 *
 * 2026-09-15 (plan-sql-series2-story.md §3 S2·10): days-since-last-order and its "gone quiet" CASE
 * (11 customers) are gone with the churn through-line; two-oclock-on-june-30 is gone because
 * Series 1 · 10 owns that argument and this lesson now only calls it back (its claim is still
 * asserted inline in §2). Every date literal is DATE '…', as in Series 1.
 *
 * ── DATES, AS VALUES ───────────────────────────────────────────────────────────────────────
 * date_trunc hands back a TIMESTAMP on PostgreSQL and a DATE on DuckDB; the drivers hand back
 * Timestamp, LocalDate or LocalDateTime. The CALENDAR VALUE is what the lesson claims, so dates are
 * compared through day(): the first ten characters, YYYY-MM-DD. How CloudBeaver PRINTS them
 * (2024-06-05 00:00:00.000) is the video's concern (timestampAsShownInCloudBeaver), not this gate's.
 *
 * ── CURRENT_DATE ───────────────────────────────────────────────────────────────────────────
 * last-30-days returns 0 on every day after 2024-07-12, which is every day this lesson can be run.
 * It is asserted as 0 together with the fact that makes it so: the last order is 2024-06-12.
 */
class DatesAndTimesSpec extends NorthwindGateSpec {

    // --- 0. The dataset's own boundaries --------------------------------------------------------

    @Unroll
    def "[#engine] orders run 2022-12-05 to 2024-06-12, shipments to 2024-06-13, every timestamp at midnight"() {
        given:
        def r = sqlFor(engine).firstRow('''SELECT min("OrderDate") AS o1, max("OrderDate") AS o2,
                                                  min("ShippedDate") AS s1, max("ShippedDate") AS s2,
                                                  count(*) AS n FROM "Orders"''')

        expect:
        day(r.o1) == "2022-12-05"
        day(r.o2) == "2024-06-12"
        day(r.s1) == "2022-12-16"
        day(r.s2) == "2024-06-13"
        r.n == 79

        and: "the article's 'every time in this table is midnight' — all three timestamp columns"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE CAST("OrderDate" AS TIME) <> TIME '00:00:00'
                                      OR CAST("RequiredDate" AS TIME) <> TIME '00:00:00'
                                      OR CAST("ShippedDate" AS TIME) <> TIME '00:00:00' ''').n == 0

        where:
        engine << ENGINES
    }

    // --- 1. TRUNCATE, DON'T EXTRACT ----------------------------------------------------------------

    @Unroll
    def "[#engine] EXTRACT folds the years: June 8, July to November 4, and June is two Junes of 4"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-month-extract"))

        expect: "two-junes' card, row for row"
        rows.collect { [dec(it.Month), dec(it.Orders)] } ==
                [[1, 8], [2, 8], [3, 9], [4, 9], [5, 9], [6, 8], [7, 4], [8, 4], [9, 4], [10, 4], [11, 4], [12, 8]]
                        .collect { [dec(it[0]), dec(it[1])] }

        and: "the trailer's split: June 2023 and June 2024 hold 4 each"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "OrderDate" >= DATE '2023-06-01' AND "OrderDate" < DATE '2023-07-01' ''').n == 4
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "OrderDate" >= DATE '2024-06-01' AND "OrderDate" < DATE '2024-07-01' ''').n == 4

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] date_trunc keeps the year: 19 months of 4 or 5"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-month"))
        def counts = [4] * 15 + [5, 5, 5, 4]

        expect: "nineteen-months' card, and the trailer's pad"
        rows.size() == 19
        day(rows.first().Month) == "2022-12-01"
        day(rows.last().Month) == "2024-06-01"
        rows*.Orders.collect { it as int } == counts
        rows.collect { day(it.Month) }.toSet().size() == 19

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] seven quarters: 4, 12, 12, 12, 12, 13, 14 — the first and the last are partial"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-quarter"))

        expect: "partial-quarters' card, row for row"
        rows.collect { [day(it.Quarter), it.Orders as int, day(it["First order"]), day(it["Last order"])] } == [
                ["2022-10-01", 4, "2022-12-05", "2022-12-26"],
                ["2023-01-01", 12, "2023-01-05", "2023-03-26"],
                ["2023-04-01", 12, "2023-04-05", "2023-06-26"],
                ["2023-07-01", 12, "2023-07-05", "2023-09-26"],
                ["2023-10-01", 12, "2023-10-05", "2023-12-26"],
                ["2024-01-01", 13, "2024-01-05", "2024-03-26"],
                ["2024-04-01", 14, "2024-04-05", "2024-06-12"],
        ]

        where:
        engine << ENGINES
    }

    // --- 2. HALF-OPEN RANGES -----------------------------------------------------------------------

    @Unroll
    def "[#engine] BETWEEN counts order 39 in July (3) and in August (4); half-open gives July 2"() {
        given:
        def july = sqlFor(engine).rows(script("shipped-in-july-between"))
        def august = sqlFor(engine).rows(script("shipped-in-august-between"))
        def halfOpen = sqlFor(engine).rows(script("shipped-in-july-half-open"))

        expect: "august-first's card"
        july.collect { [it.OrderID, day(it.ShippedDate)] } == [[36, "2023-07-08"], [37, "2023-07-16"], [39, "2023-08-01"]]

        and: "counted-twice's card"
        august.collect { [it.OrderID, day(it.ShippedDate)] } ==
                [[39, "2023-08-01"], [40, "2023-08-12"], [42, "2023-08-28"], [43, "2023-08-29"]]

        and: "half-open's card: order 39 is gone from July"
        halfOpen.collect { [it.OrderID, day(it.ShippedDate)] } == [[36, "2023-07-08"], [37, "2023-07-16"]]

        and: "August half-open is still 4, so BETWEEN's 3 + 4 = 7 describes 6 shipments"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" >= DATE '2023-08-01' AND "ShippedDate" < DATE '2023-09-01' ''').n == 4

        and: "order 39 is the ONLY shipment in the data that lands on the first of a month"
        sqlFor(engine).rows('SELECT "OrderID" AS o FROM "Orders" WHERE EXTRACT(DAY FROM "ShippedDate") = 1')*.o == [39]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the half-open callback: 14:00 on 31 July is outside BETWEEN … DATE '2023-07-31', inside the half-open range"() {
        // The article's one-paragraph callback to Series 1 · 10 (the two-oclock scene and script are
        // gone). A timestamp compared with a DATE is compared with midnight at the start of that day.
        expect:
        sqlFor(engine).firstRow('''SELECT TIMESTAMP '2023-07-31 14:00:00'
                                            BETWEEN DATE '2023-07-01' AND DATE '2023-07-31' AS b,
                                          TIMESTAMP '2023-07-31 14:00:00' >= DATE '2023-07-01'
                                            AND TIMESTAMP '2023-07-31 14:00:00' < DATE '2023-08-01' AS h''')
                .with { [it.b, it.h] } == [false, true]

        and: "on THIS table the last-day BETWEEN happens to agree (every timestamp is midnight, §0): July has 2"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" BETWEEN DATE '2023-07-01' AND DATE '2023-07-31' ''').n == 2

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the hands-on: 5 orders placed in May 2024"() {
        expect:
        sqlFor(engine).firstRow(script("orders-in-may-2024")).Orders == 5

        where:
        engine << ENGINES
    }

    // --- 3. ANCHOR ON THE DATA ---------------------------------------------------------------------

    @Unroll
    def "[#engine] the last 30 days of today: 0 — the last 30 days of the data: 7 orders"() {
        given:
        def anchored = sqlFor(engine).rows(script("last-30-days-of-data"))

        expect: "last-30-days' 0"
        sqlFor(engine).firstRow(script("last-30-days")).Orders == 0

        and: "the article's anchor: the last order minus 30 days is 2024-05-13"
        day(sqlFor(engine).firstRow('''SELECT (SELECT max("OrderDate") FROM "Orders") - INTERVAL '30 days' AS d''').d) == "2024-05-13"

        and: "anchor-result's card, row for row — the dates are all different, so the order is pinned"
        anchored.collect { [it.OrderID, day(it.OrderDate)] } == [
                [78, "2024-05-19"], [3, "2024-05-20"], [79, "2024-05-26"], [4, "2024-06-05"],
                [6, "2024-06-07"], [5, "2024-06-10"], [7, "2024-06-12"]]

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

    @Unroll
    def "[#engine] cast to DATE, then subtract: 13 slow orders, order 1 took 5 days, average 5.8, longest 9, order 6 late"() {
        given:
        def firstFive = sqlFor(engine).rows(script("days-to-ship"))
        def avg = sqlFor(engine).firstRow(script("average-days-to-ship"))
        def late = sqlFor(engine).rows(script("late-orders"))

        expect: "slow-orders' 13"
        sqlFor(engine).firstRow(script("slow-orders"))["Slow orders"] == 13

        and: "the same 13 with the interval compared with an interval — the cast changed the type, not the answer"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" - "OrderDate" > INTERVAL '7 days' ''').n == 13

        and: "the article's days-to-ship: 5, [NULL], 5, 5, [NULL]"
        firstFive.collect { [it.OrderID, it.Days == null ? null : (it.Days as int)] } ==
                [[1, 5], [2, null], [3, 5], [4, 5], [5, null]]

        and: "the article's average and longest"
        dec(avg["Average days"]) == dec("5.8")
        (avg.Longest as int) == 9

        and: "late-orders' card"
        late.collect { [it.OrderID, day(it.RequiredDate), day(it.ShippedDate)] } == [[6, "2024-06-12", "2024-06-13"]]

        where:
        engine << ENGINES
    }

    // --- 5. THE MISSING DAYS -----------------------------------------------------------------------

    @Unroll
    def "[#engine] June by GROUP BY: 4 rows; by the spine: 30 rows, 26 zeros; count(*) 30 against 4"() {
        given:
        def grouped = sqlFor(engine).rows(script("orders-per-day-june"))
        def spine = sqlFor(engine).rows(script("june-date-spine"))
        def totals = sqlFor(engine).firstRow(script("june-spine-count-star"))

        expect: "missing-days' card"
        grouped.collect { [day(it.Day), it.Orders as int] } ==
                [["2024-06-05", 1], ["2024-06-07", 1], ["2024-06-10", 1], ["2024-06-12", 1]]

        and: "spine-result: thirty days in order, the first eight as on the card, and 26 of them zero"
        spine.size() == 30
        spine.collect { day(it.Day) } == (1..30).collect { String.format("2024-06-%02d", it) }
        spine.take(8).collect { it.Orders as int } == [0, 0, 0, 0, 1, 0, 1, 0]
        spine.count { (it.Orders as int) == 0 } == 26
        spine.collect { it.Orders as int }.sum() == 4

        and: "count-star-trap's card"
        (totals["count(*)"] as int) == 30
        (totals["count(OrderID)"] as int) == 4

        where:
        engine << ENGINES
    }

    // --- 6. THE CASE FILE: SINCE WHEN? ---------------------------------------------------------------
    // plan-sql-series2-story.md §2 "Since when", measured on DuckDB 2026-09-15; asserted here on both.

    @Unroll
    def "[#engine] no ship date per quarter placed: 2, 4, 4, 4, 4, 4, 5 — 27 in all, from the first quarter"() {
        given:
        def rows = sqlFor(engine).rows(script("open-orders-per-quarter"))

        expect: "case-per-quarter's card, row for row (SUM is BIGINT on PostgreSQL, HUGEINT on DuckDB)"
        rows.collect { [day(it.Quarter), it.Orders as int, it["No ship date"] as int] } == [
                ["2022-10-01", 4, 2],
                ["2023-01-01", 12, 4],
                ["2023-04-01", 12, 4],
                ["2023-07-01", 12, 4],
                ["2023-10-01", 12, 4],
                ["2024-01-01", 13, 4],
                ["2024-04-01", 14, 5],
        ]
        rows.sum { it["No ship date"] as int } == 27
        rows.sum { it.Orders as int } == 79

        and: "the same quarters and counts as orders-per-quarter's card — only the column was added"
        sqlFor(engine).rows(script("orders-per-quarter")).collect { [day(it.Quarter), it.Orders as int] } ==
                rows.collect { [day(it.Quarter), it.Orders as int] }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] days waiting, anchored on 2024-06-12: order 8 (ALFKI, the first order in the data) 555 … order 7 0"() {
        given:
        def rows = sqlFor(engine).rows(script("open-orders-days-waiting"))
        def cells = rows.collect { [it.OrderID, it.CustomerID, day(it.OrderDate), it["Days waiting"] as int] }

        expect: "case-oldest's card: the top three and the bottom two"
        rows.size() == 27
        cells.take(3) == [[8, "ALFKI", "2022-12-05", 555], [11, "AROUT", "2022-12-26", 534], [14, "BONAP", "2023-01-19", 510]]
        cells.takeRight(2) == [[5, "ANATR", "2024-06-10", 2], [7, "BERGS", "2024-06-12", 0]]

        and: "no two orders tie, so the DESC order is the query's, not an accident"
        cells*.getAt(3).toSet().size() == 27

        and: "ALFKI is Alfreds Futterkiste, and order 8 is the first order in the data"
        sqlFor(engine).firstRow('''SELECT "CompanyName" AS n FROM "Customers" WHERE "CustomerID" = 'ALFKI' ''').n == "Alfreds Futterkiste"
        sqlFor(engine).rows('''SELECT "OrderID" AS o FROM "Orders" WHERE "OrderDate" = (SELECT min("OrderDate") FROM "Orders")''')*.o == [8]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] 25 of the 27 are past their RequiredDate — late-orders' 1 never saw them"() {
        given:
        def r = sqlFor(engine).firstRow(script("open-orders-past-required-date"))

        expect: "case-past-due's card"
        (r["No ship date"] as int) == 27
        (r["Past required date"] as int) == 25

        and: "the two not due yet are orders 5 and 7, required 2024-06-25 and 2024-06-30"
        sqlFor(engine).rows('''SELECT "OrderID" AS o, "RequiredDate" AS d FROM "Orders"
                               WHERE "ShippedDate" IS NULL
                                 AND "RequiredDate" >= (SELECT max("OrderDate") FROM "Orders")
                               ORDER BY "OrderID"''').collect { [it.o, day(it.d)] } == [[5, "2024-06-25"], [7, "2024-06-30"]]

        and: "late-orders compares the ship date, which is NULL on all 27: none of them is in its 1 row"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" IS NULL AND "ShippedDate" > "RequiredDate"''').n == 0

        and: "the article's aside: the slowest shipment took 9 days, and the SAME 25 have waited longer than that"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" IS NULL
                                     AND CAST((SELECT max("OrderDate") FROM "Orders") AS DATE) - CAST("OrderDate" AS DATE) > 9
                                     AND "RequiredDate" < (SELECT max("OrderDate") FROM "Orders")''').n == 25
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" IS NULL
                                     AND CAST((SELECT max("OrderDate") FROM "Orders") AS DATE) - CAST("OrderDate" AS DATE) > 9''').n == 25

        where:
        engine << ENGINES
    }

    // --- 7. What the KOANS stand on --------------------------------------------------------------

    @Unroll
    def "[#engine] the koan comments' facts are true"() {
        expect: "koan 1: EXTRACT(QUARTER) would give 4 groups"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM (SELECT EXTRACT(QUARTER FROM "ShippedDate") AS q
                                   FROM "Orders" WHERE "ShippedDate" IS NOT NULL GROUP BY q) AS t''').n == 4

        and: "koan 2: the EXTRACT version returned 9"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE EXTRACT(MONTH FROM "ShippedDate") = 5').n == 9

        and: "koan 2: the half-open answer agrees with the date_trunc one"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" >= DATE '2024-05-01' AND "ShippedDate" < DATE '2024-06-01' ''').n == 5

        and: "koan 4: the BETWEEN version returned 1"
        sqlFor(engine).firstRow('''SELECT count(*) AS n
                                   FROM (VALUES (TIMESTAMP '2024-01-01 00:00:00'),
                                                (TIMESTAMP '2024-03-31 18:30:00'),
                                                (TIMESTAMP '2024-04-01 00:00:00')) AS t(delivered)
                                   WHERE delivered BETWEEN DATE '2024-01-01' AND DATE '2024-03-31' ''').n == 1

        and: "koan 5: CURRENT_DATE would find no shipment in the last 7 days"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" >= CURRENT_DATE - INTERVAL '7 days' ''').n == 0

        and: "koan 9: no day in May 2024 had two shipments"
        sqlFor(engine).rows('''SELECT date_trunc('day', "ShippedDate") AS d, count(*) AS n FROM "Orders"
                               WHERE "ShippedDate" >= DATE '2024-05-01' AND "ShippedDate" < DATE '2024-06-01'
                               GROUP BY date_trunc('day', "ShippedDate") HAVING count(*) > 1''').isEmpty()

        and: "koan 10: the employees are three, and one of them took the last order"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Employees"').n == 3

        where:
        engine << ENGINES
    }

    def "koan 8's broken WHERE is refused by both engines, as its comment says"() {
        expect:
        ENGINES.every { engine ->
            try {
                sqlFor(engine).rows('SELECT count(*) FROM "Orders" WHERE "RequiredDate" - "OrderDate" > 14')
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
        titles[0] == "date_trunc keeps the year: how many quarters had a shipment"
        titles[1] == "diagnose: two Mays in one count"
        titles[2] == "a half-open range: shipped in the first quarter of 2024"
        koanQueries(titles[8])*.trim() == ["___"]
        koanQueries(titles[9])*.trim() == ["___"]
    }

    def "the video's editor mock quotes koan 1 verbatim, on the line its caret names"() {
        // ED_CODE, ED_CARET ("Ln : 87   Col : 37") and animFill in the video file hard-code koan 1's
        // blank line; if the koan moves, the mock would draw a line the file no longer has.
        given:
        def lines = koansSource().split("\n")

        expect:
        koanBody("date_trunc keeps the year: how many quarters had a shipment")
                .contains('FROM (SELECT date_trunc(___, "ShippedDate") AS "Quarter"')
        lines[86].indexOf("___") == 36
    }

    // --- helpers ---------------------------------------------------------------------------------

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
            "date_trunc keeps the year: how many quarters had a shipment"         : ["'quarter'"],
            "diagnose: two Mays in one count"                                     : ["date_trunc('month', \"ShippedDate\") = DATE '2024-05-01'"],
            "a half-open range: shipped in the first quarter of 2024"             : ["\"ShippedDate\" < DATE '2024-04-01'"],
            "diagnose: BETWEEN misses the evening of the last day"                : ["delivered >= DATE '2024-01-01' AND delivered < DATE '2024-04-01'"],
            "anchor recent on the data: the last week of shipping"                : ['(SELECT max("ShippedDate") FROM "Orders")'],
            "a date plus an INTERVAL: two weeks to deliver"                       : ["INTERVAL '14 days'"],
            "a date minus a date: whole days from order to required date"         : ['CAST("OrderDate" AS DATE)'],
            "diagnose: an INTERVAL is not a number"                               : ['CAST("RequiredDate" AS DATE) - CAST("OrderDate" AS DATE) > 14'],
            "write the whole query: every day of May 2024, with its shipments"    : ['''
                SELECT count(*) AS "Days",
                       count(o."OrderID") AS "Shipments",
                       count(*) - count(o."OrderID") AS "Days with none"
                FROM generate_series(TIMESTAMP '2024-05-01', TIMESTAMP '2024-05-31', INTERVAL '1 day') AS s(day)
                LEFT JOIN "Orders" o ON date_trunc('day', o."ShippedDate") = s.day
            '''],
            "write the whole query: days since each employee's last order"        : ['''
                SELECT e."FirstName",
                       count(*) AS "Orders",
                       CAST((SELECT max("OrderDate") FROM "Orders") AS DATE)
                       - CAST(max(o."OrderDate") AS DATE) AS "Days since last order"
                FROM "Orders" o
                JOIN "Employees" e ON e."EmployeeID" = o."EmployeeID"
                GROUP BY e."FirstName"
                ORDER BY e."FirstName"
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
