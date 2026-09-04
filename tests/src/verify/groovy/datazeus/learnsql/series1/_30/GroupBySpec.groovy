package datazeus.learnsql.series1._30

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _30 "GROUP BY".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted
 * here, on BOTH engines. The lesson's fourteen scripts:
 *
 *    1. orders-count-total          — one number for the whole table: 79
 *    2. orders-count-germany        — the manual way, one country at a time: 32
 *    3. orders-per-country          — GROUP BY: ten rows, one per country, found for you
 *    4. country-report              — several aggregates per group, which is what a report is
 *    5. orders-per-country-ranked   — ORDER BY the aggregate: Germany is the biggest market
 *    6. country-with-city           — THE RULE, as an error (asserted to FAIL)
 *    7. orders-per-city            — the report he asked for: one row per city
 *    7b. orders-per-country-city   — the same groups, with the country beside them
 *    8. cities-per-country          — two keys: the same 24 groups, plus the country
 *    9. freight-per-month-2024      — grouping by an EXPRESSION, not a column
 *   10. heavy-orders-per-country    — WHERE runs first, and the UK leaves the report
 *   11. where-with-count            — why WHERE cannot filter a total (asserted to FAIL)
 *   12. orders-per-customer         — number one by orders placed: a three-way tie
 *   13. freight-per-customer        — number one by freight paid: a different customer
 *   14. orders-per-employee         — a third grouping column, for the article
 *   15. germany-cities             — ARTICLE ONLY: where Germany's 32 went, eleven ways
 *
 * NOTHING HERE IS PINNED TO AN UNORDERED RESULT, and that is deliberate rather than lucky.
 * GROUP BY promises one row per group; it promises NOTHING about the order those rows come
 * back in — it is free to hand them over in hash-bucket order, and the two engines use
 * different hash tables. So every multi-row script in this lesson carries an ORDER BY, the
 * article and the video show them sorted, and each assertion below can therefore pin an
 * exact row order on both engines. A lesson about grouping that showed an unordered grouped
 * result would also be quietly contradicting lesson 15, which is the one that teaches this.
 *
 * THE TWO ERRORS (scripts 6 and 11) are asserted only as REFUSALS. The wording differs per
 * engine — DuckDB says "Binder Error: column ... must appear in the GROUP BY clause or must
 * be part of an aggregate function", PostgreSQL says "column \"Orders.ShipCity\" must appear
 * in the GROUP BY clause or be used in an aggregate function" (SQLSTATE 42803) — so only the
 * refusal itself is portable. The video's error panel and the article both print the
 * PostgreSQL wording, because CloudBeaver is the client this course tells learners to open.
 * Same convention as lesson 07's name-times-two.
 *
 * Convention: the spec runs the SAME *.sql files the lesson and the video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 *
 * AND THEN THE KOANS, ALL TEN, in their own section at the bottom. They deliberately do NOT
 * reuse the lesson's queries — the lesson counts ORDERS per country, the koans count PRODUCTS
 * per category and add up "UnitsInStock" — so none of the assertions above touches the data
 * they stand on. Every koan is checked here in its solved form, on both engines, plus the
 * factual claims their HINTS make: a wrong number in a hint tells a student their correct
 * query is wrong.
 */
class GroupBySpec extends NorthwindGateSpec {

    // --- 0. The dataset the lesson quotes -------------------------------------------------

    def "the dataset is the small Northwind: 79 orders across 10 countries and 24 cities"() {
        // Every "seventy-nine orders", "ten countries" and "twenty-four cities" in the article
        // and on the slides resolves to these numbers. The 10 -> 24 jump IS the trap slide:
        // a report grouped by country has ten rows, the same report grouped by country AND
        // city has twenty-four, and nothing warns you that the question changed.
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79 &&
            sqlFor(engine).firstRow('SELECT count(DISTINCT "ShipCountry") AS n FROM "Orders"').n == 10 &&
            sqlFor(engine).firstRow('SELECT count(DISTINCT "ShipCity") AS n FROM "Orders"').n == 24 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers"').n == 25 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20
        }
    }

    // --- 1. One number for the whole table, then one country at a time --------------------

    @Unroll
    def "[#engine] count(*) over the whole table is ONE number, and it is 79"() {
        expect: "the aggregate lesson's answer — the starting point this lesson argues with"
        sqlFor(engine).rows(script("orders-count-total")).size() == 1
        sqlFor(engine).firstRow(script("orders-count-total")).OrderCount == 79

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the manual way: one WHERE per country, and Germany is 32"() {
        expect:
        sqlFor(engine).firstRow(script("orders-count-germany")).OrderCount == 32

        and: "AND YOU WOULD HAVE TO RUN IT TEN TIMES — which is the argument for GROUP BY, and"
        and: "worse, you have to already know the ten names before you can write the ten WHEREs"
        sqlFor(engine).firstRow('SELECT count(DISTINCT "ShipCountry") AS n FROM "Orders"').n == 10

        and: "the video re-runs that same manual query for the next two countries Leo"
        and: "names, so both numbers on the card are the countries own, not the group row"
        sqlFor(engine).firstRow("""SELECT count(*) AS "OrderCount" FROM "Orders" WHERE "ShipCountry" = 'France'""").OrderCount == 6
        sqlFor(engine).firstRow("""SELECT count(*) AS "OrderCount" FROM "Orders" WHERE "ShipCountry" = 'Mexico'""").OrderCount == 8

        and: "AND THE TRAP THE NEXT SLIDE ACTS OUT: a name that is not in the table answers"
        and: "0, so a guessed country is indistinguishable from a real one with no orders"
        sqlFor(engine).firstRow("""SELECT count(*) AS "OrderCount" FROM "Orders" WHERE "ShipCountry" = '??? New Country'""").OrderCount == 0

        and: "while Austria was in the table the whole time, with three orders nobody"
        and: "would have thought to ask for - which is the point of naming them yourself"
        sqlFor(engine).firstRow("""SELECT count(*) AS "OrderCount" FROM "Orders" WHERE "ShipCountry" = 'Austria'""").OrderCount == 3

        and: "and Poland - the country Leo worries about - answers 0 exactly like the"
        and: "guess did, which is why he would never find out it was missing. The ten"
        and: "country report on the next slide has no Poland row either, so the card"
        and: "and the report agree"
        sqlFor(engine).firstRow("""SELECT count(*) AS "OrderCount" FROM "Orders" WHERE "ShipCountry" = 'Poland'""").OrderCount == 0

        where:
        engine << ENGINES
    }

    // --- 2. GROUP BY: one row per distinct value ------------------------------------------

    @Unroll
    def "[#engine] GROUP BY collapses 79 rows into 10, one per country, and finds the countries"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-country"))

        expect: "79 rows in, 10 rows out — the whole idea, in one number"
        rows.size() == 10

        and: "the exact report the video and the article print, alphabetically"
        rows*.ShipCountry == ["Argentina", "Austria", "France", "Germany", "Italy",
                              "Mexico", "Sweden", "UK", "USA", "Venezuela"]
        rows*.OrderCount == [3, 3, 6, 32, 3, 8, 8, 7, 3, 6]

        and: "the counts really are a partition: every order lands in exactly one group"
        rows*.OrderCount.sum() == 79

        and: "and Germany's group is the one the manual query counted by hand"
        rows.find { it.ShipCountry == "Germany" }.OrderCount ==
                sqlFor(engine).firstRow(script("orders-count-germany")).OrderCount

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] one group can carry several numbers — count, total and biggest"() {
        given:
        def rows = sqlFor(engine).rows(script("country-report"))

        expect: "the five countries we spend the most freight in"
        rows*.ShipCountry == ["Germany", "Sweden", "France", "Venezuela", "Austria"]
        rows*.OrderCount == [32, 8, 6, 6, 3]
        rows.collect { dec(it.TotalFreight) } ==
                [dec("1841.7800"), dec("410.6000"), dec("347.8500"), dec("254.3800"), dec("226.1500")]

        and: "THE POINT LEO MAKES ON THAT SLIDE: the biggest TOTAL and the biggest SINGLE"
        and: "delivery are different countries — Germany spends most overall, but Venezuela"
        and: "has the dearest one delivery in the table"
        dec(rows.find { it.ShipCountry == "Germany" }.DearestDelivery) == dec("95.7500")
        dec(rows.find { it.ShipCountry == "Venezuela" }.DearestDelivery) == dec("98.9200")
        dec(sqlFor(engine).firstRow('SELECT max("Freight") AS f FROM "Orders"').f) == dec("98.9200")

        where:
        engine << ENGINES
    }

    // --- 3. The grouped rows are rows: sort them and cut them -----------------------------

    @Unroll
    def "[#engine] ORDER BY the aggregate ranks the groups — Germany is the biggest market"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-country-ranked"))

        expect:
        rows*.ShipCountry == ["Germany", "Mexico", "Sweden", "UK", "France"]
        rows*.OrderCount == [32, 8, 8, 7, 6]

        and: "THE TIE AT 8 IS REAL, which is why the script carries a second sort key — this"
        and: "is lesson 15's rule, and grouped rows are not exempt from it"
        sqlFor(engine).rows('''SELECT "ShipCountry" FROM "Orders" GROUP BY "ShipCountry"
                               HAVING count(*) = 8''')*.ShipCountry.toSet() == ["Mexico", "Sweden"].toSet()

        where:
        engine << ENGINES
    }

    // --- 4. THE RULE, as an error ---------------------------------------------------------

    @Unroll
    def "[#engine] a column that is neither grouped nor aggregated is REFUSED"() {
        when: "asking for the ShipCity beside a count per country"
        sqlFor(engine).rows(script("country-with-city"))

        then: "the database stops you rather than picking one of Germany's eleven cities"
        // The MESSAGE differs per engine — see the header. Only the refusal is portable.
        thrown(Exception)

        where:
        engine << ENGINES
    }

    def "the refusal is honest: Germany's group really does hold 11 different cities"() {
        // The slide's whole explanation is that the database cannot show one "ShipCity" for
        // Germany because there are eleven of them. If that number were one, the error would
        // look like pedantry instead of protection — and the trap two slides later, where those
        // eleven cities split Germany's single row apart, would have nothing to split.
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('''SELECT count(DISTINCT "ShipCity") AS n FROM "Orders"
                                       WHERE "ShipCountry" = 'Germany' ''').n == 11
        }
    }

    // --- 5. THE TRAP: obeying the error changes the question -------------------------------

    @Unroll
    def "[#engine] grouping by the city gives the report that was actually asked for"() {
        given: "the country report, and the same question asked per city"
        def byCountry = sqlFor(engine).rows(script("orders-per-country-ranked"))
        def byCity = sqlFor(engine).rows(script("orders-per-city"))

        expect: "five real rows, every number a real count"
        byCity.size() == 5

        and: "the exact five rows the video puts on screen"
        byCity*.ShipCity == ["M\u00e9xico D.F.", "Berlin", "Lule\u00e5", "London", "Aachen"]
        byCity*.OrderCount == [8, 5, 5, 4, 3]

        and: "Germany topped the country report with 32 and is absent here \u2014 it is not a city"
        byCountry.first().ShipCountry == "Germany"
        byCountry.first().OrderCount == 32
        byCity.every { it.ShipCity != "Germany" }

        and: "its 32 orders are spread over 11 cities, the biggest of them Berlin with 5"
        sqlFor(engine).firstRow('''SELECT count(DISTINCT "ShipCity") AS n FROM "Orders"
                                   WHERE "ShipCountry" = 'Germany' ''').n == 11
        byCity.find { it.ShipCity == "Berlin" }.OrderCount == 5

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the country beside the city changes the display, not the groups"() {
        given: "the same report, grouped by country and city together"
        def byCity = sqlFor(engine).rows(script("orders-per-city"))
        def withCountry = sqlFor(engine).rows(script("orders-per-country-city"))

        expect: "THE CLAIM THE SLIDE MAKES: no city here sits in two countries, so the two"
        and: "groupings produce exactly the same number of groups"
        sqlFor(engine).rows('''SELECT "ShipCity" FROM "Orders" GROUP BY "ShipCity"''').size() ==
                sqlFor(engine).rows('''SELECT "ShipCountry", "ShipCity" FROM "Orders"
                                       GROUP BY "ShipCountry", "ShipCity"''').size()

        and: "so the same five rows come back, with the country spelled out beside each"
        withCountry*.ShipCity == byCity*.ShipCity
        withCountry*.OrderCount == byCity*.OrderCount
        withCountry*.ShipCountry == ["Mexico", "Germany", "Sweden", "UK", "Germany"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the grain changed: ten rows became twenty-four, and Germany alone is eleven"() {
        expect: "the same query without the LIMIT — one row per city"
        sqlFor(engine).rows('''SELECT "ShipCity" FROM "Orders"
                               GROUP BY "ShipCity"''').size() == 24

        and: "against ten rows for the report that was actually asked for"
        sqlFor(engine).rows(script("orders-per-country")).size() == 10

        and: "Germany's single row of 32 is eleven rows once the city splits it"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM (
                                     SELECT "ShipCity" FROM "Orders"
                                     WHERE "ShipCountry" = 'Germany'
                                     GROUP BY "ShipCity") g''').n == 11

        and: "and Mexico only wins because all eight of its orders sit in ONE city"
        sqlFor(engine).firstRow('''SELECT count(DISTINCT "ShipCity") AS n FROM "Orders"
                                   WHERE "ShipCountry" = 'Mexico' ''').n == 1

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] where Germany's 32 went: eleven cities, none bigger than five"() {
        // THE ARTICLE'S TABLE, and the clearest statement of what the trap actually did. It is
        // article-only on purpose — the video says it in a sentence, because a table repeating
        // a sentence would sit between the trap springing and the rule landing.
        given:
        def rows = sqlFor(engine).rows(script("germany-cities"))

        expect: "eleven rows where the country report had one"
        rows.size() == 11
        rows*.ShipCity == ["Berlin", "Aachen", "Brandenburg", "Frankfurt a.M.", "Köln",
                           "Leipzig", "Mannheim", "München", "Cunewalde", "Münster", "Stuttgart"]
        rows.collect { it.OrderCount as int } == [5, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2]

        and: "NOTHING WAS LOST — the eleven still add up to the 32 the country report showed"
        rows.collect { it.OrderCount as int }.sum() == 32

        and: "and the biggest of them is 5, which is the row that appears in the trap's top five"
        rows.first().ShipCity == "Berlin"
        (rows.first().OrderCount as int) == 5

        and: "THE ORDER IS SAFE ACROSS ENGINES even with the umlaut cities: every comparison is"
        and: "decided on an ASCII character before an umlaut is reached, and this was run on both"
        rows*.ShipCity.every { it ==~ /[A-Za-zÀ-ÿ. ]+/ }

        where:
        engine << ENGINES
    }

    // --- 6. The same two keys, used on purpose --------------------------------------------

    @Unroll
    def "[#engine] two keys give the SAME groups as the city alone, plus the country"() {
        given:
        def rows = sqlFor(engine).rows(script("cities-per-country"))

        expect: "one row per city, and every city carries its country"
        rows.size() == 24

        and: "THE CLAIM THE SLIDE MAKES: grouping by the city alone gives the same 24 groups,"
        and: "because no city in this data sits in two countries"
        sqlFor(engine).rows('''SELECT "ShipCity" FROM "Orders" GROUP BY "ShipCity"''').size() == 24
        sqlFor(engine).rows('''SELECT "ShipCity" FROM "Orders"
                               GROUP BY "ShipCity"
                               HAVING count(DISTINCT "ShipCountry") > 1''').isEmpty()

        and: "the first six rows the video puts on screen, in country-then-city order"
        rows.take(6)*.ShipCountry == ["Argentina", "Austria", "France", "France", "Germany", "Germany"]
        rows.take(6)*.ShipCity == ["Buenos Aires", "Graz", "Marseille", "Nantes", "Aachen", "Berlin"]
        rows.take(6)*.OrderCount == [3, 3, 3, 3, 3, 5]

        and: "and the counts still add up to the country report — nothing was thrown away"
        rows.findAll { it.ShipCountry == "Germany" }*.OrderCount.sum() == 32
        rows*.OrderCount.sum() == 79

        where:
        engine << ENGINES
    }

    // --- 7. Grouping by an expression -----------------------------------------------------

    @Unroll
    def "[#engine] freight per month for 2024 — grouped by something no column holds"() {
        given:
        def rows = sqlFor(engine).rows(script("freight-per-month-2024"))

        expect: "six months of 2024, one row each"
        rows.size() == 6
        rows.collect { it.Month as int } == [1, 2, 3, 4, 5, 6]
        rows.collect { dec(it.TotalFreight) } == [dec("100.4200"), dec("212.9000"), dec("350.4800"),
                                                  dec("182.3600"), dec("226.3400"), dec("112.2400")]

        and: "March is the peak the slide points at"
        dec(rows.max { dec(it.TotalFreight) }.TotalFreight) == dec("350.4800")
        (rows.max { dec(it.TotalFreight) }.Month as int) == 3

        and: "THERE IS NO Month COLUMN TO GROUP BY — which is the whole reason the slide exists"
        sqlFor(engine).rows('''SELECT column_name FROM information_schema.columns
                               WHERE table_name = 'Orders' ''')*.column_name.every { it != "Month" }

        where:
        engine << ENGINES
    }

    // --- 8. WHERE runs FIRST, and a group can leave the report -----------------------------

    @Unroll
    def "[#engine] WHERE filters rows before grouping, so the UK vanishes rather than showing 0"() {
        given:
        def all = sqlFor(engine).rows(script("orders-per-country"))
        def heavy = sqlFor(engine).rows(script("heavy-orders-per-country"))

        expect: "ten countries before the filter, NINE after it"
        all.size() == 10
        heavy.size() == 9

        and: "THE BUG THE SLIDE IS BUILT ON: the UK is simply not in the report"
        "UK" in all*.ShipCountry
        !("UK" in heavy*.ShipCountry)

        and: "and it is not a data error — the UK has seven orders, none over 50 in freight"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShipCountry" = 'UK' ''').n == 7
        dec(sqlFor(engine).firstRow('''SELECT max("Freight") AS f FROM "Orders"
                                       WHERE "ShipCountry" = 'UK' ''').f) == dec("45.5000")

        and: "the nine rows the video shows"
        heavy*.ShipCountry == ["Germany", "France", "Austria", "Sweden", "Argentina",
                               "USA", "Italy", "Mexico", "Venezuela"]
        heavy*.OrderCount == [20, 4, 3, 3, 2, 2, 1, 1, 1]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] an aggregate in WHERE is REFUSED — which is what lesson 35 is for"() {
        when: "trying to keep only the countries with more than five orders"
        sqlFor(engine).rows(script("where-with-count"))

        then: "WHERE sees one row at a time, before any group exists, so there is no count yet"
        // DuckDB: "WHERE clause cannot contain aggregates!"
        // PostgreSQL: "aggregate functions are not allowed in WHERE" (SQLSTATE 42803)
        thrown(Exception)

        where:
        engine << ENGINES
    }

    def "and the answer that error is asking for is real: six countries have more than five"() {
        // The slide promises lesson 35 will produce this list. If the promise is empty the
        // hand-off is worthless, so the gate checks there is something on the other side.
        // HAVING is NOT taught in this lesson and appears nowhere in its scripts — it is here
        // only to prove the question has an answer.
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).rows('''SELECT "ShipCountry" FROM "Orders"
                                   GROUP BY "ShipCountry" HAVING count(*) > 5
                                   ORDER BY "ShipCountry"''')*.ShipCountry ==
                    ["France", "Germany", "Mexico", "Sweden", "UK", "Venezuela"]
        }
    }

    // --- 9. The payoff: number one by which measure? ---------------------------------------

    @Unroll
    def "[#engine] number one by orders placed is a THREE-WAY TIE at five"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-customer"))

        expect:
        rows*.CustomerID == ["ALFKI", "ANATR", "BERGS", "AROUT", "ANTON"]
        rows*.OrderCount == [5, 5, 5, 4, 3]

        and: "THE TIE IS REAL — three customers have five orders each and nothing separates"
        and: "them, so 'our number one customer' has no single answer by this measure"
        sqlFor(engine).rows('''SELECT "CustomerID" FROM "Orders" GROUP BY "CustomerID"
                               HAVING count(*) = 5''')*.CustomerID.toSet() ==
                ["ALFKI", "ANATR", "BERGS"].toSet()

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] number one by freight paid is FRANK — a customer not even in the other top three"() {
        given:
        def rows = sqlFor(engine).rows(script("freight-per-customer"))

        expect:
        rows*.CustomerID == ["FRANK", "ALFKI", "FOLKO", "ERNSH", "DUMON"]
        rows.collect { dec(it.TotalFreight) } == [dec("268.3300"), dec("253.7300"), dec("247.2400"),
                                                  dec("226.1500"), dec("205.0600")]

        and: "THE WHOLE POINT OF THE CLOSING SLIDE: the measure decides the answer. FRANK tops"
        and: "the freight list with only three orders; two of the three customers who tied on"
        and: "order count are not at the top of this one at all."
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "CustomerID" = 'FRANK' ''').n == 3
        !("ANATR" in rows*.CustomerID)
        !("BERGS" in rows*.CustomerID)

        and: "no tie anywhere near the cut, so this list cannot wobble between runs"
        rows.collect { dec(it.TotalFreight) }.toSet().size() == 5

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] a third grouping column: three employees, and Employee 1 handled the most"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-per-employee"))

        expect:
        rows*.EmployeeID == [1, 3, 2]
        rows*.OrderCount == [28, 27, 24]

        and: "the three counts are distinct, so no second sort key is needed here"
        rows*.OrderCount.toSet().size() == 3

        and: "and they account for every order — one employee per order, none missing"
        rows*.OrderCount.sum() == 79

        where:
        engine << ENGINES
    }

    // --- 9b. What the SHORT stands on -----------------------------------------------------

    @Unroll
    def "[#engine] the SHORT's two tables: the top three flip from Germany to Mexico"() {
        // The vertical short (learnsql-series1-30-short-group-by) puts exactly these two
        // three-row tables on screen, and its entire argument is the difference between them.
        // They are the top of the two scripts above, so they are already gated — but the short
        // COMPACTS the SQL to fit a 1080-wide card (`AS n` for `AS "OrderCount"`), and a
        // compacted query is a second copy of a query. This asserts the compacted form really
        // does return the rows the short draws, on both engines, so the two cannot drift apart.
        given: "the short's first card, compacted from orders-per-country-ranked"
        def before = sqlFor(engine).rows('''SELECT "ShipCountry", count(*) AS n FROM "Orders"
                                            GROUP BY "ShipCountry"
                                            ORDER BY n DESC, "ShipCountry" LIMIT 3''')

        and: "and its second, compacted from orders-per-country-city"
        def after = sqlFor(engine).rows('''SELECT "ShipCountry", "ShipCity", count(*) AS n
                                           FROM "Orders"
                                           GROUP BY "ShipCountry", "ShipCity"
                                           ORDER BY n DESC, "ShipCountry" LIMIT 3''')

        expect: "the hook: Germany 32"
        before*.ShipCountry == ["Germany", "Mexico", "Sweden"]
        before.collect { it.n as int } == [32, 8, 8]

        and: "and the turn: the same data, a different country on top, and Germany at 5"
        after*.ShipCountry == ["Mexico", "Germany", "Sweden"]
        after*.ShipCity == ["México D.F.", "Berlin", "Luleå"]
        after.collect { it.n as int } == [8, 5, 5]

        and: "THE HOOK'S CLAIM IN ONE LINE — same data, and Germany reads 32 or 5"
        (before.find { it.ShipCountry == "Germany" }.n as int) == 32
        (after.find { it.ShipCountry == "Germany" }.n as int) == 5

        where:
        engine << ENGINES
    }

    // --- 10. What the KOANS stand on -------------------------------------------------------
    //
    // THE KOANS DO NOT REUSE THE LESSON'S QUERIES. The lesson counts ORDERS per country; the
    // koans count PRODUCTS per category, add up "UnitsInStock" per supplier, and count
    // CUSTOMERS per country. That is the house convention — pom.xml states it as "the koans
    // are related practice, not a blanked copy of the gate" — and it exists so a learner
    // applies the idea somewhere new instead of retyping a query they just watched.
    //
    // Which is exactly why the koans need their own assertions. Nothing in the sections above
    // touches "CategoryID", "SupplierID", "UnitsInStock" or "Discontinued", so a shift in that
    // data would surface as a RED KOAN ON A STUDENT'S SCREEN with a green gate behind it —
    // the worst possible place to discover it.
    //
    // Every koan is asserted with the SQL its solved form produces, on BOTH engines. The koans
    // run on DuckDB only, but each is written to give the same answer in CloudBeaver against
    // PostgreSQL; if that stops being true, a learner checking their work is told they are
    // wrong when they are right.

    def "the koan file header's schema claims are true"() {
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "Discontinued"').n == 2 &&
            sqlFor(engine).firstRow('SELECT count(DISTINCT "CategoryID") AS n FROM "Products"').n == 8 &&
            sqlFor(engine).firstRow('SELECT count(DISTINCT "SupplierID") AS n FROM "Products"').n == 6 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers"').n == 25 &&
            sqlFor(engine).firstRow('SELECT count(DISTINCT "Country") AS n FROM "Customers"').n == 10 &&
            sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Customers"
                                       WHERE "Country" = 'Germany' ''').n == 11 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "OrderDate" IS NULL').n == 0
        }
    }

    @Unroll
    def "[#engine] the koan header's claim that categories and suppliers CUT ACROSS each other"() {
        // Koans 5 and 6 are both built on this: if any category came from exactly one supplier,
        // adding "SupplierID" to the GROUP BY would not split it and koan 5 would have no trap.
        expect: "every one of the eight categories is supplied by more than one supplier"
        sqlFor(engine).rows('''SELECT "CategoryID", count(DISTINCT "SupplierID") AS n
                               FROM "Products" GROUP BY "CategoryID"
                               ORDER BY "CategoryID"''').every { (it.n as int) > 1 }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koans 1 and 2: 20 products in 8 categories, and category 8 holds the most stock"() {
        given: "koan 1 — one row per distinct CategoryID"
        def counts = sqlFor(engine).rows('''SELECT "CategoryID", count(*) AS n FROM "Products"
                                            GROUP BY "CategoryID" ORDER BY "CategoryID"''')

        and: "koan 2 — the same groups, a different aggregate"
        def stock = sqlFor(engine).rows('''SELECT "CategoryID", sum("UnitsInStock") AS s FROM "Products"
                                           GROUP BY "CategoryID" ORDER BY "CategoryID"''')

        expect:
        counts*.CategoryID == [1, 2, 3, 4, 5, 6, 7, 8]
        counts.collect { it.n as int } == [3, 3, 2, 3, 3, 2, 2, 2]
        counts.collect { it.n as int }.sum() == 20

        and: "koan 2's answer"
        stock.collect { it.s as int } == [76, 105, 35, 41, 95, 29, 50, 154]

        and: "koan 2's HINT: category 8 has only two products and the most stock of all"
        (counts.find { it.CategoryID == 8 }.n as int) == 2
        (stock.max { it.s as int }.CategoryID) == 8

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 3: the three categories holding the most stock"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT "CategoryID", sum("UnitsInStock") AS "TotalStock"
                                          FROM "Products" GROUP BY "CategoryID"
                                          ORDER BY "TotalStock" DESC LIMIT 3''')

        expect:
        rows*.CategoryID == [8, 2, 5]
        rows.collect { it.TotalStock as int } == [154, 105, 95]

        and: "no tie at the cut, so LIMIT 3 has exactly one right answer"
        sqlFor(engine).rows('''SELECT sum("UnitsInStock") AS s FROM "Products"
                               GROUP BY "CategoryID" ORDER BY s DESC LIMIT 4''')
                .collect { it.s as int }.toSet().size() == 4

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 4: the cheapest and dearest line in each category"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT "CategoryID", min("UnitPrice") AS lo,
                                                 max("UnitPrice") AS hi
                                          FROM "Products" GROUP BY "CategoryID"
                                          ORDER BY "CategoryID"''')

        expect:
        rows*.CategoryID == [1, 2, 3, 4, 5, 6, 7, 8]
        rows.collect { dec(it.lo) } == ["4.5000", "10.0000", "12.5000", "12.5000",
                                        "7.0000", "97.0000", "23.2500", "18.4000"].collect { dec(it) }
        rows.collect { dec(it.hi) } == ["19.0000", "22.0000", "17.4500", "34.0000",
                                        "38.0000", "123.7900", "30.0000", "31.0000"].collect { dec(it) }

        and: "koan 4's HINT: category 6 runs from 97.0000 to 123.7900"
        dec(rows.find { it.CategoryID == 6 }.lo) == dec("97.0000")
        dec(rows.find { it.CategoryID == 6 }.hi) == dec("123.7900")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koans 5 and 6: the same six products, two rows or five, depending on the grain"() {
        given: "koan 5 SOLVED — per category, which is what was asked for"
        def perCategory = sqlFor(engine).rows('''SELECT "CategoryID", count(*) AS n FROM "Products"
                                                 WHERE "CategoryID" IN (1, 2)
                                                 GROUP BY "CategoryID" ORDER BY "CategoryID"''')

        and: "koan 6 SOLVED — per category AND supplier, which is a different question"
        def perSupplier = sqlFor(engine).rows('''SELECT "CategoryID", "SupplierID", count(*) AS n
                                                 FROM "Products" WHERE "CategoryID" IN (1, 2)
                                                 GROUP BY "CategoryID", "SupplierID"
                                                 ORDER BY "CategoryID", "SupplierID"''')

        expect: "koan 5: two rows, three products each"
        perCategory*.CategoryID == [1, 2]
        perCategory.collect { it.n as int } == [3, 3]

        and: "koan 6: five rows, and koan 5's HINT names exactly this — 2, 1, 1, 1, 1"
        perSupplier.size() == 5
        perSupplier*.CategoryID == [1, 1, 2, 2, 2]
        perSupplier*.SupplierID == [1, 2, 1, 2, 4]
        perSupplier.collect { it.n as int } == [2, 1, 1, 1, 1]

        and: "SAME SIX PRODUCTS EITHER WAY — the rows did not change, only the piling did"
        perCategory.collect { it.n as int }.sum() == 6
        perSupplier.collect { it.n as int }.sum() == 6

        and: "and koan 5's HINT that those six come from four different suppliers"
        perSupplier*.SupplierID.toSet().size() == 3
        sqlFor(engine).firstRow('''SELECT count(DISTINCT "SupplierID") AS n FROM "Products"
                                   WHERE "CategoryID" IN (1, 2)''').n == 3

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 7: orders per year, grouped by an expression"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT EXTRACT(YEAR FROM "OrderDate") AS "Year",
                                                 count(*) AS n
                                          FROM "Orders"
                                          GROUP BY EXTRACT(YEAR FROM "OrderDate")
                                          ORDER BY "Year"''')

        expect:
        rows.collect { it.Year as int } == [2022, 2023, 2024]
        rows.collect { it.n as int } == [4, 48, 27]

        and: "the koan's HINT: December 2022 to June 2024, and 2022 is a stub with four orders"
        rows.collect { it.n as int }.sum() == 79
        (sqlFor(engine).firstRow('SELECT min("OrderDate") AS d FROM "Orders"').d as String).startsWith("2022-12")
        (sqlFor(engine).firstRow('SELECT max("OrderDate") AS d FROM "Orders"').d as String).startsWith("2024-06")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 8: filtering out the discontinued lines makes category 6 disappear"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT "CategoryID", count(*) AS n FROM "Products"
                                          WHERE "Discontinued" = false
                                          GROUP BY "CategoryID" ORDER BY "CategoryID"''')

        expect: "SEVEN rows where koan 1 returned eight"
        rows*.CategoryID == [1, 2, 3, 4, 5, 7, 8]
        rows.collect { it.n as int } == [3, 3, 2, 3, 3, 2, 2]

        and: "THE KOAN'S WHOLE POINT: category 6 is gone, not zero"
        !(6 in rows*.CategoryID)

        and: "because BOTH discontinued products are in category 6 — which is what makes it"
        and: "the only category that can vanish, and therefore the only one worth asking about"
        sqlFor(engine).rows('''SELECT "CategoryID" FROM "Products"
                               WHERE "Discontinued"''')*.CategoryID.toSet() == [6].toSet()
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Products"
                                   WHERE "CategoryID" = 6''').n == 2

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 9: Germany runs away with it, and three countries tie on two"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT "Country", count(*) AS "CustomerCount"
                                          FROM "Customers" GROUP BY "Country"
                                          ORDER BY "CustomerCount" DESC, "Country" LIMIT 4''')

        expect:
        rows*.Country == ["Germany", "France", "Mexico", "Sweden"]
        rows.collect { it.CustomerCount as int } == [11, 2, 2, 2]

        and: "THE TIE IS REAL — without the second sort key the fourth row is undecided"
        sqlFor(engine).rows('''SELECT "Country" FROM "Customers" GROUP BY "Country"
                               HAVING count(*) = 2''')*.Country.toSet() ==
                ["France", "Mexico", "Sweden", "UK", "Venezuela"].toSet()

        and: "and every tie is settled on a plain ASCII first character, so no collation"
        and: "difference between the engines can reach it"
        rows*.Country.every { it ==~ /[A-Za-z ]+/ }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 10: where our stock is sitting, once the dead lines are excluded"() {
        given: "the whole query the student writes from scratch"
        def rows = sqlFor(engine).rows('''SELECT "SupplierID", sum("UnitsInStock") AS "TotalStock"
                                          FROM "Products"
                                          WHERE "Discontinued" = false
                                          GROUP BY "SupplierID"
                                          ORDER BY "TotalStock" DESC LIMIT 3''')

        expect:
        rows*.SupplierID == [3, 4, 6]
        rows.collect { it.TotalStock as int } == [144, 143, 76]

        and: "THE FILTER HAS TO MATTER — leave it out and the top TWO swap, which is what the"
        and: "koan's hint promises. A student who forgets the WHERE gets a visibly wrong answer"
        and: "rather than a subtly wrong one."
        def unfiltered = sqlFor(engine).rows('''SELECT "SupplierID", sum("UnitsInStock") AS "TotalStock"
                                                FROM "Products" GROUP BY "SupplierID"
                                                ORDER BY "TotalStock" DESC LIMIT 3''')
        unfiltered*.SupplierID == [4, 3, 6]
        unfiltered.collect { it.TotalStock as int } == [172, 144, 76]

        and: "and the 29 units the hint blames are supplier 4's one discontinued line"
        sqlFor(engine).firstRow('''SELECT sum("UnitsInStock") AS s FROM "Products"
                                   WHERE "SupplierID" = 4 AND "Discontinued"''').s as int == 29

        and: "no tie at the cut, so LIMIT 3 has exactly one right answer"
        sqlFor(engine).rows('''SELECT sum("UnitsInStock") AS s FROM "Products"
                               WHERE "Discontinued" = false GROUP BY "SupplierID"
                               ORDER BY s DESC LIMIT 4''').collect { it.s as int }.toSet().size() == 4

        where:
        engine << ENGINES
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/30-group-by/scripts/${name}.sql").text
    }

    /** Freight is DECIMAL(19,4) on DuckDB and numeric on PostgreSQL, and the two hand back
     *  different Java types with different scales. Compare by VALUE, never by toString or
     *  by ==, or 12.5 and 12.5000 stop being equal for reasons that have nothing to teach. */
    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }
}
