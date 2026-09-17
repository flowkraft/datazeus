package datazeus.learnsql.series2._20

import datazeus.support.NorthwindCoGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 2 · lesson _20
 * "CTEs (WITH) — Breaking a Long Query Into Named Steps".
 *
 * Runs on NORTHWIND COMPANY S (schema northwind_co_s) on BOTH engines, each re-checksummed against
 * `_dataset_info` before any assertion (NorthwindCoGateSpec). Every figure the video, the article,
 * the trailer, the short and the koans put in front of a learner is asserted here. The lesson's
 * scripts, and the sections that run them:
 *
 *    customers-above-the-average-customer-nested, average-customer-with,
 *    customers-above-the-average-customer-with                                §1 name it, then use it
 *    dairy-report-with, dairy-report-with-check                               §2 steps that read steps
 *    dairy-report-tidied, dairy-report-tidied-check                           §3 the tidy-up
 *    sales-and-freight-with, sales-and-freight-with-check                     §4 one filter, every use sees it
 *    sales-and-freight-june                                                   §4b the hands-on
 *    month-orders-step                                                        §4c look inside one step
 *
 * §5 asserts the data facts the lesson leans on and every number the KOANS' comments state, and §6
 * runs the koans file itself, as written, on both engines — including the two EQUIVALENCE koans,
 * whose expected rows are whatever the given nested query returns — plus, on DuckDB, the comparison
 * exactly as KoanBase makes it (Groovy ==).
 *
 * ── A REWRITE IS ONLY A REWRITE IF THE ANSWER DID NOT MOVE ────────────────────────────────
 * The WITH versions are compared ROW FOR ROW with the queries they rewrite, which live in other
 * lessons' scripts folders: Series 2 · 00's nested customer query, Series 2 · 15's Dairy cure
 * (dairy-units-narrowed-first) and its sales-and-freight cure (sales-and-freight-aggregated-first).
 * If either lesson's script changes, this gate fails.
 *
 * ── NO STORY IN THIS EPISODE (plan-academy-course-stories-artefacts.md §3.2.1, row 20) ───────
 * The drafted case parts (the delivered re-run, the report by rep and courier) are gone. Its beat
 * "one filter, every use of the step sees it" is taught on the sales-and-freight report: May's range,
 * written twice in Series 2 · 15's cure, is written once in the month_orders step (§4).
 *
 * ── THE TWO REVIEW NOTES IN THE VIDEO'S HEADER, MEASURED HERE ─────────────────────────────
 *  1. The tidy-up returns EXACTLY Series 2 · 15's left-then-inner rows (asserted in §3): the trap
 *     still repeats the previous lesson. The open alternative ("merge two steps and the freight
 *     multiplies again") is measured in §4 and NOT on screen: rows 47 = 47, sales 262931.17 =
 *     262931.17, freight 28515.7 against 8977.88, Nordic Foods back on top with 2149.26.
 *  2. The check the lesson shows on the freight report includes the freight, so it catches that
 *     merge; a rows-and-sales check would not (asserted in §4).
 */
class CtesSpec extends NorthwindCoGateSpec {

    static final String S15 = "../courses/learnsql/series2-intermediate/15-multi-table-joins-duplicate-rows/scripts/"

    // --- 1. NAME IT, THEN USE IT -------------------------------------------------------------------

    def "the nested query on screen is the subqueries lesson's script, byte for byte"() {
        expect:
        script("customers-above-the-average-customer-nested") ==
                new File("../courses/learnsql/series2-intermediate/00-subqueries/scripts/customers-above-the-average-customer.sql").text
    }

    @Unroll
    def "[#engine] WITH customer_totals: the average customer is 126193.54, and the same 30 customers as the nested query"() {
        given:
        def nested = sqlFor(engine).rows(script("customers-above-the-average-customer-nested"))
        def with = sqlFor(engine).rows(script("customers-above-the-average-customer-with"))
        def asCard = { List rows -> rows.collect { [it.CompanyName, dec(it["Total sales"])] } }

        expect: "with-one's figure"
        dec(sqlFor(engine).firstRow(script("average-customer-with"))["Average customer"]) == dec("126193.54")

        and: "same-thirty: row for row the nested query's answer"
        with.size() == 30
        asCard(with) == asCard(nested)
        asCard(with).take(3) == [["Fjord Foods", dec("797164")], ["Yarrow Pantry", dec("786022.56")], ["Quayside Traders", dec("783579.03")]]
        asCard(with).takeRight(2) == [["Amber Grocers", dec("186853.36")], ["Emerald Grocers", dec("175249.7")]]

        and: "no two totals are equal, so the order on the card is the same on both engines"
        with.collect { dec(it["Total sales"]) }.unique().size() == 30

        and: "the nested query really is three brackets deep (the trailer's claim)"
        script("customers-above-the-average-customer-nested").count("(SELECT") == 2

        and: "the WITH version writes the step once and reads it twice"
        script("customers-above-the-average-customer-with").count("FROM customer_totals") == 2
        script("customers-above-the-average-customer-with").count("(SELECT") == 1

        where:
        engine << ENGINES
    }

    // --- 2. STEPS THAT READ STEPS -------------------------------------------------------------------

    @Unroll
    def "[#engine] the Dairy report as steps returns exactly last lesson's cure: 10 rows, 7 sold, 1891 units"() {
        given:
        def with = sqlFor(engine).rows(script("dairy-report-with"))
        def cure = sqlFor(engine).rows(new File(S15 + "dairy-units-narrowed-first.sql").text)
        def check = sqlFor(engine).firstRow(script("dairy-report-with-check"))
        def asCard = { List rows -> rows.collect { [it.ProductName, it["May units"] == null ? null : (it["May units"] as int)] } }

        expect: "row for row"
        asCard(with) == asCard(cure)
        asCard(with) == [["Alpine Butter", null], ["Classic Feta", null], ["Coastal Parmesan", 10], ["Harvest Butter", 408],
                         ["Heritage Butter", 836], ["Heritage Gouda", 58], ["Highland Yoghurt", 52], ["Island Cheddar", null],
                         ["Rustic Butter", 471], ["Smoked Feta", 56]]

        and: "same-answer's card"
        [check.Rows as int, check.Sold as int, check.Units as int] == [10, 7, 1891]

        and: "may_units reads may_lines, and May is written once"
        script("dairy-report-with").contains("FROM may_lines")
        script("dairy-report-with").count("DATE '2024-05-01'") == 1

        where:
        engine << ENGINES
    }

    // --- 3. THE TIDY-UP ----------------------------------------------------------------------------

    @Unroll
    def "[#engine] the tidy-up: 7 rows, and the units still add up to 1891 — only the row count notices"() {
        given:
        def tidied = sqlFor(engine).rows(script("dairy-report-tidied"))
        def check = sqlFor(engine).firstRow(script("dairy-report-tidied-check"))
        def asCard = { List rows -> rows.collect { [it.ProductName, it["May units"] as int] } }

        expect: "seven-rows' card"
        asCard(tidied) == [["Coastal Parmesan", 10], ["Harvest Butter", 408], ["Heritage Butter", 836], ["Heritage Gouda", 58],
                           ["Highland Yoghurt", 52], ["Rustic Butter", 471], ["Smoked Feta", 56]]

        and: "equivalent-check's second row"
        [check.Rows as int, check.Sold as int, check.Units as int] == [7, 7, 1891]

        and: "the three products it lost are exactly the three with no May sale"
        (sqlFor(engine).rows(script("dairy-report-with")).findAll { it["May units"] == null }*.ProductName) ==
                ["Alpine Butter", "Classic Feta", "Island Cheddar"]

        and: "REVIEW NOTE 1: these are the very rows of the joins lesson's left-then-inner card"
        asCard(tidied) == asCard(sqlFor(engine).rows(new File(S15 + "dairy-units-left-then-inner.sql").text))

        where:
        engine << ENGINES
    }

    // --- 4. ONE FILTER, EVERY USE OF THE STEP SEES IT ---------------------------------------------------

    @Unroll
    def "[#engine] May written once: the steps return last lesson's sales-and-freight cure row for row, and the check matches on rows, sales AND freight"() {
        given:
        def cureSrc = new File(S15 + "sales-and-freight-aggregated-first.sql").text
        def with = sqlFor(engine).rows(script("sales-and-freight-with"))
        def cure = sqlFor(engine).rows(cureSrc)
        def check = sqlFor(engine).firstRow(script("sales-and-freight-with-check"))
        def asCard = { List rows -> rows.collect { [it.CompanyName, dec(it["Total sales"]), dec(it.Freight)] } }

        expect: "may-twice: last lesson's cure writes May's range twice; the steps write it once"
        cureSrc.count("DATE '2024-05-01'") == 2
        script("sales-and-freight-with").count("DATE '2024-05-01'") == 1
        script("sales-and-freight-with").count("FROM month_orders") == 2

        and: "may-once-result's card, row for row the cure"
        asCard(with) == asCard(cure)
        asCard(with) == [["Alpine Provisions", dec("15686.56"), dec("610.83")], ["Golden Pantry", dec("18342.33"), dec("562.08")],
                         ["Yarrow Pantry", dec("12884.6"), dec("502.33")], ["Quayside Traders", dec("13170.94"), dec("497.47")],
                         ["Nordic Foods", dec("11322.73"), dec("480.29")]]

        and: "freight-check's card: 47 rows, 262931.17, 8977.88"
        [check.Rows as int, dec(check.Sales), dec(check.Freight)] == [47, dec("262931.17"), dec("8977.88")]

        and: "'the same as last lesson's version, before its LIMIT'"
        def cureAll = sqlFor(engine).rows(cureSrc.replace("LIMIT 5;", ""))
        cureAll.size() == 47
        cureAll.collect { new BigDecimal(it["Total sales"].toString()) }.sum().stripTrailingZeros() == dec("262931.17")
        cureAll.collect { new BigDecimal(it.Freight.toString()) }.sum().stripTrailingZeros() == dec("8977.88")

        and: "8977.88 is May's whole freight bill, over Orders alone: 181 orders, 47 customers"
        def may = sqlFor(engine).firstRow('''SELECT SUM("Freight") AS f, count(*) AS n, count(DISTINCT "CustomerID") AS c FROM "Orders"
                                             WHERE "OrderDate" >= DATE '2024-05-01' AND "OrderDate" < DATE '2024-06-01' ''')
        [dec(may.f), may.n as int, may.c as int] == [dec("8977.88"), 181, 47]

        and: "LIMIT 5 cuts cleanly: the sixth row is below the fifth"
        dec(sqlFor(engine).rows(script("sales-and-freight-with").replace("LIMIT 5;", "LIMIT 6;"))[5].Freight) < dec("480.29")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] may-twice: change one bracket to June and forget the other — no error, 39 customers, June's sales beside May's freight"() {
        given: "the joins lesson's cure with ONLY the sales bracket moved to June"
        def src = new File(S15 + "sales-and-freight-aggregated-first.sql").text
        def forgot = src.replace("o.\"OrderDate\" >= DATE '2024-05-01'", "o.\"OrderDate\" >= DATE '2024-06-01'")
                        .replace("o.\"OrderDate\" <  DATE '2024-06-01'", "o.\"OrderDate\" <  DATE '2024-07-01'")

        expect: "exactly one bracket changed"
        forgot != src
        forgot.count("DATE '2024-05-01'") == 1
        forgot.count("DATE '2024-07-01'") == 1

        and: "it runs, and quietly reports 39 customers instead of 47"
        sqlFor(engine).rows(forgot.replace("LIMIT 5;", "")).size() == 39

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] REVIEW NOTE 2 and the open alternative: merge sales and freight into one step and only the freight in the check notices"() {
        // Not on screen. The owner's open curriculum question for 20; the check the lesson shows must catch it.
        given:
        def merged = '''WITH month_orders AS (
                          SELECT "OrderID", "CustomerID", "Freight" FROM "Orders"
                          WHERE "OrderDate" >= DATE '2024-05-01' AND "OrderDate" < DATE '2024-06-01'),
                        customer_totals AS (
                          SELECT o."CustomerID",
                                 ROUND(SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")), 2) AS "Total sales",
                                 SUM(o."Freight") AS "Freight"
                          FROM month_orders o JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                          GROUP BY o."CustomerID"),
                        report AS (
                          SELECT c."CompanyName", t."Total sales", t."Freight"
                          FROM "Customers" c JOIN customer_totals t ON t."CustomerID" = c."CustomerID")'''
        def m = sqlFor(engine).firstRow(merged + ' SELECT count(*) AS "Rows", sum("Total sales") AS "Sales", sum("Freight") AS "Freight" FROM report')
        def top = sqlFor(engine).rows(merged + ' SELECT "CompanyName", "Freight" FROM report ORDER BY "Freight" DESC LIMIT 1')[0]

        expect: "rows and sales match the right answer; freight does not"
        [m.Rows as int, dec(m.Sales)] == [47, dec("262931.17")]
        dec(m.Freight) == dec("28515.7")
        [top.CompanyName, dec(top.Freight)] == ["Nordic Foods", dec("2149.26")]

        where:
        engine << ENGINES
    }

    // --- 4b. THE HANDS-ON: CHANGE THE MONTH IN ONE PLACE -------------------------------------------------

    @Unroll
    def "[#engine] the hands-on: June 2024 in the one step — Juniper Trading first, on 772.19"() {
        given:
        def june = script("sales-and-freight-june")
        def rows = sqlFor(engine).rows(june)

        expect: "only the two dates changed"
        june == script("sales-and-freight-with").replace(">= DATE '2024-05-01'", ">= DATE '2024-06-01'")
                                                 .replace("<  DATE '2024-06-01'", "<  DATE '2024-07-01'")

        and: "the article's card"
        rows.collect { [it.CompanyName, dec(it["Total sales"]), dec(it.Freight)] } == [
                ["Juniper Trading", dec("21727.16"), dec("772.19")], ["Zenith Food Hall", dec("18151.04"), dec("715.64")],
                ["Fjord Foods", dec("17594.89"), dec("625.96")], ["Golden Pantry", dec("15954.95"), dec("536.37")],
                ["Alpine Provisions", dec("15670.92"), dec("528.21")]]
        dec(sqlFor(engine).rows(june.replace("LIMIT 5;", "LIMIT 6;"))[5].Freight) < dec("528.21")

        where:
        engine << ENGINES
    }

    // --- 4c. LOOK INSIDE ONE STEP ------------------------------------------------------------------------

    @Unroll
    def "[#engine] one step alone: month_orders for Nordic Foods holds four orders, 480.29 of freight"() {
        given:
        def rows = sqlFor(engine).rows(script("month-orders-step"))

        expect: "month-orders-result's card"
        rows.collect { [it.OrderID as int, it.CustomerID, dec(it.Freight)] } ==
                [[8374, "NORDI", dec("184.43")], [8468, "NORDI", dec("59.69")], [8482, "NORDI", dec("44.27")], [8548, "NORDI", dec("191.9")]]
        rows.collect { new BigDecimal(it.Freight.toString()) }.sum().stripTrailingZeros() == dec("480.29")
        sqlFor(engine).firstRow('''SELECT "CompanyName" AS n FROM "Customers" WHERE "CustomerID" = 'NORDI' ''').n == "Nordic Foods"

        and: "the step is the one in the report, word for word"
        script("sales-and-freight-with").startsWith(script("month-orders-step").readLines().take(6).join("\n"))

        where:
        engine << ENGINES
    }

    // --- 5. THE DATA FACTS, AND WHAT THE KOANS STAND ON -----------------------------------------------

    @Unroll
    def "[#engine] the dataset is Northwind Company S"() {
        expect:
        ['Orders': 10000, 'Order Details': 25233, 'Products': 80, 'Customers': 120, 'Employees': 12, 'Categories': 8, 'Shippers': 4].every { t, n ->
            (sqlFor(engine).firstRow("SELECT count(*) AS n FROM \"${t}\"".toString()).n as int) == n
        }

        and: "the average customer is over the 119 customers who ordered (one never did)"
        sqlFor(engine).firstRow('SELECT count(DISTINCT "CustomerID") AS n FROM "Orders"').n as int == 119

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the koan comments' facts are true"() {
        expect: "koan 2: the average category is 1877128.89"
        dec(sqlFor(engine).firstRow('''SELECT ROUND(AVG(t."Total"), 2) AS a FROM (
                                         SELECT p."CategoryID", SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
                                         FROM "Products" p JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                         GROUP BY p."CategoryID") t''').a) == dec("1877128.89")

        and: "koan 3: nine reps take orders, and the third and fourth averages differ (LIMIT 3 cuts cleanly)"
        def reps = sqlFor(engine).rows('''SELECT o."EmployeeID", SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) / count(DISTINCT o."OrderID") AS p
                                          FROM "Orders" o JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                                          GROUP BY o."EmployeeID" ORDER BY p DESC''')
        reps.size() == 9
        new BigDecimal(reps[2].p.toString()) > new BigDecimal(reps[3].p.toString()) + 1

        and: "koan 4: the shares, unrounded, are nowhere near a rounding edge"
        def ch = sqlFor(engine).rows('''SELECT o."Channel" AS c, SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS s
                                        FROM "Orders" o JOIN "Order Details" d ON d."OrderID" = o."OrderID" GROUP BY o."Channel"''')
        def all = ch.collect { new BigDecimal(it.s.toString()) }.sum()
        ch.every { r ->
            def pct = new BigDecimal(r.s.toString()) * 100 / all
            def frac = (pct * 10).remainder(BigDecimal.ONE).abs()
            (frac - 0.5).abs() > 0.02
        }

        and: "koan 6: the tidied version (the day's test in the final WHERE) returns 6 rows of 8"
        sqlFor(engine).rows('''WITH lines AS (SELECT p."CategoryID", d."Quantity", o."OrderDate"
                                             FROM "Products" p JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                             JOIN "Orders" o ON o."OrderID" = d."OrderID")
                               SELECT c."CategoryName", sum(l."Quantity") AS u FROM "Categories" c
                               LEFT JOIN lines l ON l."CategoryID" = c."CategoryID"
                               WHERE l."OrderDate" >= DATE '2023-02-18' AND l."OrderDate" < DATE '2023-02-19'
                               GROUP BY c."CategoryName"''').size() == 6

        and: "koan 9: 21 countries, the average country is 715096.72"
        def countries = sqlFor(engine).firstRow('''SELECT count(*) AS n, ROUND(AVG(t."Total"), 2) AS a FROM (
                                     SELECT c."Country", SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
                                     FROM "Customers" c JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                                     JOIN "Order Details" d ON d."OrderID" = o."OrderID" GROUP BY c."Country") t''')
        (countries.n as int) == 21
        dec(countries.a) == dec("715096.72")

        and: "koan 10: three segments, and every one bought something in August 2023"
        sqlFor(engine).firstRow('SELECT count(DISTINCT "Segment") AS n FROM "Customers"').n as int == 3
        sqlFor(engine).firstRow('''SELECT count(DISTINCT c."Segment") AS n FROM "Customers" c
                                   JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                                   WHERE o."OrderDate" >= DATE '2023-08-01' AND o."OrderDate" < DATE '2023-09-01' ''').n as int == 3

        where:
        engine << ENGINES
    }

    // --- 6. THE KOAN FILE ITSELF, RUN AS WRITTEN -----------------------------------------------------

    @Unroll
    def "[#engine] koan file, as written: '#title' runs and returns exactly what it claims"() {
        given:
        def sql = koanSql(title)
        def expected = koanExpected(engine, title)

        expect:
        resultOf(engine, sql, expected) == expected

        where:
        [engine, title] << [ENGINES, ANSWERS.keySet() as List].combinations()
    }

    @Unroll
    def "[duckdb, as KoanBase compares] '#title' passes the learner's own check"() {
        // KoanBase: rows(sql) as plain Lists, compared with the koan's expected value by Groovy ==.
        // A DOUBLE where the koan wrote a decimal would still pass plain() here and fail for the learner.
        given:
        def sql = koanSql(title)
        def db = sqlFor("duckdb")
        def raw = { String q -> db.rows(q).collect { r -> r.values().toList() } }
        def nested = koanNested(title)

        expect:
        nested != null ? raw(sql) == raw(nested)
                : (koanRawExpected(title) instanceof List ? raw(sql) == koanRawExpected(title)
                   : db.firstRow(sql).values().toList()[0] == koanRawExpected(title))

        where:
        title << (ANSWERS.keySet() as List)
    }

    def "the koans file matches the video's koans slide: ten koans, its three names, two equivalence koans, two whole queries last"() {
        given:
        def titles = (koansSource() =~ /(?m)^    def "(.+?)"\(\)/).collect { it[1] }

        expect:
        titles == (ANSWERS.keySet() as List)
        titles.size() == 10
        titles[0] == "name it, then use the name: the three biggest categories"
        titles[1] == "use a step twice: categories above the average category"
        titles[2] == "one step reads another: sales per order, per sales rep"
        titles.findAll { koanNested(it) != null } == ["equivalent: rewrite the nested product query with WITH",
                                                      "equivalent: rewrite the courier report with WITH"]
        koanQueries(titles[8])*.trim() == ["___"]
        koanQueries(titles[9])*.trim() == ["___"]
        koansSource().contains("extends NorthwindCoKoanBase")
    }

    def "the video's editor mock quotes koan 1 verbatim, on the line its caret names"() {
        // ED_CODE, ED_CARET ("Ln : 96   Col : 18") and animFill hard-code koan 1's blank line.
        given:
        def lines = koansSource().split("\n")

        expect:
        lines[95] == "            FROM ___"
        lines[95].indexOf("___") == 17
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static String script(String name) {
        new File("../courses/learnsql/series2-intermediate/20-ctes/scripts/${name}.sql").text
    }

    private static String koansSource() {
        new File("src/koans/groovy/datazeus/learnsql/series2/_20/CtesKoans.groovy").text
    }

    private static String koanBody(String title) {
        def src = koansSource()
        int at = src.indexOf('def "' + title + '"()')
        assert at >= 0: "no koan titled '${title}' in the koans file — it was renamed or removed"
        int next = src.indexOf('\n    def "', at + 1)
        next < 0 ? src.substring(at) : src.substring(at, next)
    }

    private static List<String> koanQueries(String title) {
        (koanBody(title) =~ /(?s)'''(.*?)'''/).collect { it[1] }.findAll { it.contains("___") }
    }

    /** An equivalence koan's given, correct, nested query — null for an ordinary koan. */
    private static String koanNested(String title) {
        def body = koanBody(title)
        if (!body.contains("shouldReturn(nested,")) return null
        def all = (body =~ /(?s)'''(.*?)'''/).collect { it[1] }
        all.find { !it.contains("___") }
    }

    /** THE INTENDED ANSWER FOR EACH BLANK, in koan order. */
    private static final Map<String, List<String>> ANSWERS = [
            "name it, then use the name: the three biggest categories"            : ["category_totals"],
            "use a step twice: categories above the average category"             : ['(SELECT AVG("Total") FROM category_totals)'],
            "one step reads another: sales per order, per sales rep"              : ["rep_totals"],
            "a total of totals: each channel's share of all sales"                : ["channel_sales"],
            "equivalent: rewrite the nested product query with WITH"              : ['''SELECT d."ProductID", sum(d."Quantity") AS "Units"
              FROM "Order Details" d
              JOIN "Orders" o ON o."OrderID" = d."OrderID"
              WHERE o."OrderDate" >= DATE '2024-01-01' AND o."OrderDate" < DATE '2025-01-01'
              GROUP BY d."ProductID"'''],
            "diagnose: the tidy-up that dropped two categories"                   : ["o.\"OrderDate\" >= DATE '2023-02-18' AND o.\"OrderDate\" < DATE '2023-02-19'"],
            "run one step on its own"                                             : ["day_lines"],
            "equivalent: rewrite the courier report with WITH"                    : ['''SELECT o."ShipVia", count(*) AS "Lines"
              FROM "Orders" o
              JOIN "Order Details" d ON d."OrderID" = o."OrderID"
              GROUP BY o."ShipVia"'''],
            "write the whole query: countries above the average country"          : ['''
                WITH country_totals AS (
                  SELECT c."Country",
                         SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
                  FROM "Customers" c
                  JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                  JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                  GROUP BY c."Country"
                )
                SELECT "Country", ROUND("Total", 2) AS "Total"
                FROM country_totals
                WHERE "Total" > (SELECT AVG("Total") FROM country_totals)
                ORDER BY "Total" DESC
            '''],
            "write the whole query: customers and August 2023 units per segment"  : ['''
                WITH segment_customers AS (
                  SELECT "Segment", count(*) AS "Customers"
                  FROM "Customers"
                  GROUP BY "Segment"
                ),
                august_units AS (
                  SELECT c."Segment", sum(d."Quantity") AS "Units"
                  FROM "Customers" c
                  JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                  JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                  WHERE o."OrderDate" >= DATE '2023-08-01' AND o."OrderDate" < DATE '2023-09-01'
                  GROUP BY c."Segment"
                )
                SELECT sc."Segment", sc."Customers", a."Units"
                FROM segment_customers sc
                LEFT JOIN august_units a ON a."Segment" = sc."Segment"
                ORDER BY sc."Segment"
            '''],
    ]

    private static String koanSql(String title) {
        def qs = koanQueries(title)
        assert qs.size() == 1: "koan '${title}' has ${qs.size()} queries with a blank; this helper expects one"
        def sql = qs[0]
        def answers = ANSWERS[title]
        int found = sql.count("___")
        assert found == answers.size(): "koan '${title}' has ${found} blank(s) but the answer table has ${answers.size()}"
        answers.each { a -> sql = sql.replaceFirst(/___/, java.util.regex.Matcher.quoteReplacement(a)) }
        sql
    }

    private Object koanExpected(String engine, String title) {
        def nested = koanNested(title)
        if (nested != null) {
            return sqlFor(engine).rows(nested).collect { r -> (0..<r.size()).collect { i -> plain(r.getAt(i)) } }
        }
        def raw = koanRawExpected(title)
        raw instanceof List ? (raw as List).collect { row -> (row as List).collect { plain(it) } } : plain(raw)
    }

    private static Object koanRawExpected(String title) {
        def body = koanBody(title)
        def rows = (body =~ /(?s)shouldReturn\(\s*(\[.*?\])\s*,\s*'''/)
        if (rows.find()) return Eval.me(rows.group(1))
        def single = (body =~ /shouldReturn\s+(-?\d+(?:\.\d+)?)\s*,\s*'''/)
        assert single.find(): "koan '${title}' declares neither rows nor a single value with shouldReturn"
        new BigDecimal(single.group(1))
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
