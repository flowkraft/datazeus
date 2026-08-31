package datazeus.learnsql.series1._20

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

import java.sql.SQLException

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _20 "Aliases, Expressions & DISTINCT".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted here,
 * on BOTH engines. The lesson's thirteen scripts:
 *
 *    1. stock-value-unnamed          — an expression with no alias, and the name each engine
 *                                      invents for it. THIS IS THE SLIDE episode 15 promised.
 *    2. customers-renamed            — AS on a plain column, and a heading with a space in it
 *    3. stock-value                  — the expression named, and the alias reused by ORDER BY
 *    4. stock-value-alias-in-where   — THE ERROR: PostgreSQL 42703. DuckDB accepts it.
 *    5. stock-value-over-1000        — the portable fix: say the calculation twice
 *    6. trade-price-unrounded        — arithmetic makes it WORSE: four decimals become six
 *    7. trade-price                  — ROUND(…, 2), and the two engines agree exactly
 *    8. call-sheet                   — || joins text, and the spaces are the author's job
 *    9. countries-one-per-customer   — 25 rows for a question with 10 answers
 *   10. countries-distinct           — 10
 *   11. countries-and-cities         — THE TRAP: 24, with Germany in it eleven times
 *   12. price-list                   — the finished report the episode closes on
 *   13. incoming-total               — the mid-lesson hands-on query
 *
 * TWO ASSERTIONS HERE ARE DELIBERATELY PER ENGINE, and they are the two the lesson is honest
 * about rather than the two it hides:
 *
 *   THE INVENTED COLUMN NAME (script 1). PostgreSQL calls an unnamed expression "?column?";
 *   DuckDB calls it "(UnitPrice * UnitsInStock)". Both are useless to a reader and neither is
 *   wrong, and the VIDEO shows PostgreSQL's because CloudBeaver is what this course tells a
 *   learner to open. If either engine ever changes its mind, the slide and the article are both
 *   wrong and this is what says so.
 *
 *   THE ALIAS IN WHERE (script 4). PostgreSQL rejects it with SQLSTATE 42703 — the error panel
 *   in the video is that exact message — and the SQL standard agrees with PostgreSQL. DuckDB
 *   ACCEPTS it and returns the three rows. That difference is stated out loud on the slide, and
 *   it is the whole reason this idea has no koan: the koans run on DuckDB, so a koan would go
 *   green on the answer CloudBeaver refuses. See the header of DistinctAliasesExpressionsKoans.
 *
 * Convention: the spec runs the SAME *.sql files the lesson and the video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 *
 * AND THEN THE KOANS, ALL TEN, in their own section at the bottom. They deliberately do NOT
 * reuse the lesson's queries — they price up ORDER LINES and tidy the SUPPLIER list, where the
 * lesson names columns on the catalogue and counts countries on the customer list — so nothing
 * in the sections above touches the data they stand on. Every koan is checked in its solved
 * form, on both engines, along with every factual claim their HINTS make. A wrong number in a
 * hint tells a student their correct query is wrong, which is worse than no hint at all.
 */
class DistinctAliasesExpressionsSpec extends NorthwindGateSpec {

    // --- 0. The dataset the whole lesson counts on ----------------------------------------

    def "the dataset is the small Northwind the lesson quotes: 20 products, 25 customers, 6 suppliers"() {
        // Every "twenty-five customers", "ten countries" and "eleven Germans" on a slide
        // resolves to these numbers. This is the SMALL Northwind, not the 91-customer original,
        // so looking an answer up elsewhere gives a different one.
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers"').n == 25 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Suppliers"').n == 6 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Order Details"').n == 193
        }
    }

    // --- 1. The expression nobody named ---------------------------------------------------

    @Unroll
    def "[#engine] the stock-value expression returns the five right numbers"() {
        given:
        def rows = sqlFor(engine).rows(script("stock-value-unnamed"))

        expect: "the five most valuable lines on the shelf, biggest first"
        rows.size() == 5
        rows*.ProductName == ["Mishi Kobe Niku", "Boston Crab Meat", "Chef Antons Cajun Seasoning",
                              "Ikura", "Tofu"]

        and: "and the numbers on the slide, to the last decimal place"
        rows.collect { dec(it[1]) } == ["2813.0000", "2263.2000", "1166.0000", "961.0000",
                                        "813.7500"].collect { dec(it) }

        where:
        engine << ENGINES
    }

    def "PostgreSQL calls an unnamed expression ?column? — which is the slide"() {
        // EPISODE 15 CLOSES ON THIS EXACT STRING ("that heading still says ?column?"), the
        // video's column-question slide puts it on screen, and the article prints it. It is a
        // PostgreSQL behaviour, asserted alone rather than @Unroll'd, because DuckDB answers
        // differently and the next assertion is where that is pinned.
        expect:
        labels("postgres", script("stock-value-unnamed")) == ["ProductName", "?column?"]
    }

    def "DuckDB invents a different useless name for the same column"() {
        // Not "?column?" — DuckDB echoes the expression back. Also useless to a reader, which
        // is the point the slide makes: NEITHER engine can name it for you, so you must.
        expect:
        labels("duckdb", script("stock-value-unnamed")) == ["ProductName", "(UnitPrice * UnitsInStock)"]
    }

    // --- 2. AS on a plain column ----------------------------------------------------------

    @Unroll
    def "[#engine] AS renames a column, and a quoted alias keeps its spaces and capitals"() {
        given:
        def rows = sqlFor(engine).rows(script("customers-renamed"))

        expect: "the headings are the ones the author chose, verbatim"
        // THE SPACES AND THE CAPITAL ARE THE ASSERTION. An UNQUOTED alias would come back
        // folded to lower case on PostgreSQL ("who to ask for"), which is the gotcha the
        // article covers and the reason the lesson quotes every alias it writes.
        labels(engine, script("customers-renamed")) == ["Customer", "Who to ask for"]

        and: "and the data is untouched — only what it is called changed"
        rows*.Customer == ["Alfreds Futterkiste", "Ana Trujillo Emparedados y helados",
                           "Antonio Moreno Taquería", "Around the Horn", "Berglunds snabbköp"]
        rows*."Who to ask for" == ["Maria Anders", "Ana Trujillo", "Antonio Moreno",
                                   "Thomas Hardy", "Christina Berglund"]

        where:
        engine << ENGINES
    }

    // --- 3. Naming the expression, and the sort reusing the name --------------------------

    @Unroll
    def "[#engine] the named expression sorts by its own alias and returns the same five rows"() {
        given:
        def named = sqlFor(engine).rows(script("stock-value"))

        and: "the same query before it was named"
        def unnamed = sqlFor(engine).rows(script("stock-value-unnamed"))

        expect: "the headings are now readable"
        labels(engine, script("stock-value")) == ["Product", "Stock value"]

        and: "ORDER BY \"Stock value\" gave exactly what ORDER BY <the whole expression> gave"
        // THE CLAIM THE SLIDE MAKES — "once a thing has a name, the sort can use the name" —
        // proved by comparing the two queries rather than by restating the rows.
        named*.Product == unnamed*.ProductName
        named.collect { dec(it."Stock value") } == unnamed.collect { dec(it[1]) }

        and: "the top line really is one the company has stopped selling, which is the payoff"
        named.first().Product == "Mishi Kobe Niku"
        sqlFor(engine).firstRow('''SELECT "Discontinued" AS d FROM "Products"
                                   WHERE "ProductName" = 'Mishi Kobe Niku' ''').d

        where:
        engine << ENGINES
    }

    // --- 4. How far the name reaches — and this is where the engines part company ----------

    def "PostgreSQL refuses an alias in WHERE, with the exact error the video draws"() {
        // THE ERROR PANEL IS THIS, not a paraphrase: SQLSTATE 42703, column "Stock value" does
        // not exist. If PostgreSQL ever changes the wording, the slide is wrong and this fails.
        when:
        sqlFor("postgres").rows(script("stock-value-alias-in-where"))

        then:
        SQLException e = thrown()
        e.SQLState == "42703"
        e.message.contains('"Stock value"')
        e.message.contains("does not exist")
    }

    def "DuckDB ACCEPTS the same query, which is why this idea has no koan"() {
        // The koans run on DuckDB. If a koan asked a learner to fix `WHERE "Stock value"`, the
        // unfixed version would go GREEN there while CloudBeaver rejected it — so the rule is
        // taught in the video, the article and the deck, and the koan file says why it is
        // absent. This assertion is what keeps that reasoning honest: the day DuckDB tightens
        // up, this goes red and a koan becomes possible.
        expect:
        sqlFor("duckdb").rows(script("stock-value-alias-in-where")).size() == 3
    }

    @Unroll
    def "[#engine] repeating the calculation in WHERE works everywhere, and finds three lines"() {
        given:
        def rows = sqlFor(engine).rows(script("stock-value-over-1000"))

        expect:
        rows*.Product == ["Mishi Kobe Niku", "Boston Crab Meat", "Chef Antons Cajun Seasoning"]

        and: "every one of them really is over a thousand, and the fourth really is not"
        rows.every { dec(it."Stock value") > dec("1000") }
        dec(sqlFor(engine).firstRow('''SELECT "UnitPrice" * "UnitsInStock" AS v FROM "Products"
                                       ORDER BY "UnitPrice" * "UnitsInStock" DESC
                                       LIMIT 1 OFFSET 3''').v) < dec("1000")

        where:
        engine << ENGINES
    }

    // --- 5. ROUND -------------------------------------------------------------------------

    @Unroll
    def "[#engine] multiplying a 4-decimal price by 0.85 hands back SIX decimal places"() {
        given:
        def rows = sqlFor(engine).rows(script("trade-price-unrounded"))

        expect: "the exact strings the slide shows — the trailing zeros are the point"
        // COMPARED AS TEXT ON PURPOSE, and this is the one place in the file where that is
        // right: the slide's whole claim is about how many decimal places come back, and
        // dec() would strip precisely the thing being asserted.
        rows.collect { it."Trade price".toString() } ==
                ["105.221500", "82.450000", "32.300000", "28.900000", "26.350000"]

        and: "the input really was four places, so the arithmetic ADDED two"
        sqlFor(engine).firstRow('''SELECT "UnitPrice" AS p FROM "Products"
                                   WHERE "ProductName" = 'Thuringer Rostbratwurst' ''')
                .p.toString() == "123.7900"

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] ROUND to two places, and the two engines agree to the last cent"() {
        given:
        def rows = sqlFor(engine).rows(script("trade-price"))

        expect:
        rows*.Product == ["Thuringer Rostbratwurst", "Mishi Kobe Niku", "Gnocchi di nonna Alice",
                          "Camembert Pierrot", "Ikura"]
        rows.collect { dec(it."Trade price") } ==
                ["105.22", "82.45", "32.30", "28.90", "26.35"].collect { dec(it) }

        and: "ROUND is HALF AWAY FROM ZERO on both engines — 10.625 goes to 10.63, not 10.62"
        // Worth pinning: the two engines agreeing on the half case is not something the SQL
        // standard promises, and the article states it as a fact.
        dec(sqlFor(engine).firstRow('SELECT ROUND(10.625, 2) AS r').r) == dec("10.63")

        and: "and ROUND changed the OUTPUT only — the stored price is exactly what it was"
        sqlFor(engine).firstRow('''SELECT "UnitPrice" AS p FROM "Products"
                                   WHERE "ProductName" = 'Thuringer Rostbratwurst' ''')
                .p.toString() == "123.7900"

        where:
        engine << ENGINES
    }

    // --- 6. Text expressions --------------------------------------------------------------

    @Unroll
    def "[#engine] the double pipe joins text, and the spaces come from the author"() {
        given:
        def rows = sqlFor(engine).rows(script("call-sheet"))

        expect:
        rows*."Who to call" == ["Maria Anders at Alfreds Futterkiste",
                                "Ana Trujillo at Ana Trujillo Emparedados y helados",
                                "Antonio Moreno at Antonio Moreno Taquería",
                                "Thomas Hardy at Around the Horn",
                                "Christina Berglund at Berglunds snabbköp"]

        and: "THE SLIDE'S WARNING IS TRUE: drop the spaces inside the quotes and the whole line"
        and: "runs together, with no error at all"
        // WRITTEN OUT IN FULL, and checked, because the first draft of this lesson guessed it.
        // The obvious guess is "Maria Andersat Alfreds Futterkiste" — as though only the FIRST
        // join lost its space. It is not: 'at' has no space on either side, so BOTH joins close
        // up and you get "AndersatAlfreds". The video and the article both quote this string, so
        // a wrong guess here would have shipped on a slide.
        sqlFor(engine).firstRow('''SELECT "ContactName" || 'at' || "CompanyName" AS c
                                   FROM "Customers" WHERE "CustomerID" = 'ALFKI' ''').c ==
                "Maria AndersatAlfreds Futterkiste"

        where:
        engine << ENGINES
    }

    // --- 7. DISTINCT, and the trap the episode is built on ---------------------------------

    @Unroll
    def "[#engine] asking for the country column gives one row per CUSTOMER — 25 of them"() {
        given:
        def rows = sqlFor(engine).rows(script("countries-one-per-customer"))

        expect:
        rows.size() == 25

        and: "and Germany is in it eleven times, which is the number the slide says out loud"
        rows*.Country.count("Germany") == 11

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] DISTINCT collapses them to the ten countries that are actually there"() {
        given:
        def rows = sqlFor(engine).rows(script("countries-distinct"))

        expect:
        rows.size() == 10
        rows*.Country == ["Argentina", "Austria", "France", "Germany", "Italy",
                          "Mexico", "Sweden", "UK", "USA", "Venezuela"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] THE TRAP: adding a column makes a DISTINCT list more than twice as long"() {
        given: "the same query with one more column, and the word DISTINCT still in it"
        def trap = sqlFor(engine).rows(script("countries-and-cities"))

        and: "the honest answer to the question that was asked"
        def countries = sqlFor(engine).rows(script("countries-distinct"))

        expect: "24 rows where the question has 10 answers, and no error on the way"
        trap.size() == 24
        countries.size() == 10

        and: "THE BUG, stated as the thing a reader would actually notice: Germany, eleven times"
        trap*.Country.count("Germany") == 11

        and: "and it is not a subtle drift — every country with more than one city repeats"
        // The mechanism, asserted rather than described: the row is unique because the CITY is,
        // so DISTINCT keeps it. Nothing about the country column decided anything.
        trap*.Country.unique().size() == 10
        trap.size() > countries.size()

        and: "the first six rows the video puts on screen"
        trap.take(6).collect { [it.Country, it.City] } ==
                [["Argentina", "Buenos Aires"], ["Austria", "Graz"],
                 ["France", "Marseille"], ["France", "Nantes"],
                 ["Germany", "Aachen"], ["Germany", "Berlin"]]

        where:
        engine << ENGINES
    }

    def "SELECT DISTINCT with an ORDER BY on an unselected column: PostgreSQL refuses, DuckDB allows"() {
        // ARTICLE-ONLY, and deliberately not in the video — one engine-split per episode is
        // enough. It is asserted here because the article states it as a fact, and because it
        // is the same shape as the alias-in-WHERE difference: the strict engine is the one the
        // learner is actually pointed at.
        when:
        sqlFor("postgres").rows('SELECT DISTINCT "Country" FROM "Customers" ORDER BY "CustomerID"')

        then:
        SQLException e = thrown()
        e.SQLState == "42P10"

        and: "while DuckDB simply answers"
        sqlFor("duckdb").rows('SELECT DISTINCT "Country" FROM "Customers" ORDER BY "CustomerID"').size() == 10
    }

    // --- 8. The finished report, and the hands-on query ------------------------------------

    @Unroll
    def "[#engine] the price list the episode closes on"() {
        given:
        def rows = sqlFor(engine).rows(script("price-list"))

        expect: "three named headings"
        labels(engine, script("price-list")) == ["Product", "List price", "Trade price"]

        and: "the five rows on the final slide"
        rows*.Product == ["Gnocchi di nonna Alice", "Camembert Pierrot", "Ikura",
                          "Uncle Bobs Organic Dried Pears", "Tofu"]
        rows.collect { dec(it."List price") } ==
                ["38.00", "34.00", "31.00", "30.00", "23.25"].collect { dec(it) }
        rows.collect { dec(it."Trade price") } ==
                ["32.30", "28.90", "26.35", "25.50", "19.76"].collect { dec(it) }

        and: "TOFU IS THE ROW THE SLIDE HIGHLIGHTS — the only one ROUND actually cut short"
        dec(sqlFor(engine).firstRow('''SELECT "UnitPrice" * 0.85 AS v FROM "Products"
                                       WHERE "ProductName" = 'Tofu' ''').v) == dec("19.7625")

        and: "THE WHERE HAS TO MATTER: the two dearest products in the catalogue are both"
        and: "discontinued, so a reader can SEE that the filter removed something"
        sqlFor(engine).rows('''SELECT "ProductName" FROM "Products" WHERE "Discontinued"
                               ORDER BY "ProductName"''')*.ProductName ==
                ["Mishi Kobe Niku", "Thuringer Rostbratwurst"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the hands-on query: what we will hold once the incoming orders land"() {
        given:
        def rows = sqlFor(engine).rows(script("incoming-total"))

        expect:
        rows*.Product == ["Boston Crab Meat", "Aniseed Syrup", "Gorgonzola Telino",
                          "Chang", "Chef Antons Cajun Seasoning"]
        rows*."Total units" == [123, 83, 70, 57, 53]

        and: "no tie anywhere near the cut, so the five cannot wobble between runs"
        sqlFor(engine).rows('''SELECT "UnitsInStock" + "UnitsOnOrder" AS t FROM "Products"
                               ORDER BY t DESC LIMIT 6''')*.t.toSet().size() == 6

        where:
        engine << ENGINES
    }

    // --- 9. What the KOANS stand on --------------------------------------------------------
    //
    // THE KOANS DO NOT REUSE THE LESSON'S QUERIES. The lesson names columns on "Products" and
    // counts countries on "Customers"; the koans price up "Order Details" and tidy "Suppliers".
    // That is the house convention — pom.xml states it as "the koans are related practice, not
    // a blanked copy of the gate" — and it exists so a learner applies the idea somewhere new.
    //
    // Which is exactly why the koans need their own assertions. Nothing above touches
    // "Order Details" or "Suppliers", so a shift in that data would surface as a RED KOAN ON A
    // STUDENT'S SCREEN with a green gate behind it — the worst possible place to find it.
    //
    // Every koan is asserted with the SQL its solved form produces, on BOTH engines. The koans
    // run on DuckDB only, but each is written to give the same answer in CloudBeaver against
    // PostgreSQL; if that stops being true, a learner checking their work is told they are
    // wrong when they are right.

    @Unroll
    def "[#engine] koan 1: a quoted alias keeps its space, and ORDER BY can use it"() {
        given: "the koan's solved form"
        def rows = sqlFor(engine).rows('''SELECT "CompanyName" AS "Supplier",
                                                 "Country" AS "Home country"
                                          FROM "Suppliers"
                                          ORDER BY "Home country", "Supplier"
                                          LIMIT 3''')

        expect:
        rows.collect { [it.Supplier, it."Home country"] } ==
                [["Pavlova Ltd", "Australia"], ["Pasta Buttini s.r.l.", "Italy"],
                 ["Tokyo Traders", "Japan"]]

        and: "the hint's claim: sorting by country really does put Australia on top"
        rows.first()."Home country" == "Australia"

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 2: order 4's first line is 12 at 18.0000, so the line total is 216"() {
        expect: "the koan's answer"
        dec(sqlFor(engine).firstRow('''SELECT "UnitPrice" * "Quantity" AS v FROM "Order Details"
                                       WHERE "OrderID" = 4 AND "ProductID" = 1''').v) == dec("216.0000")

        and: "the hint's two numbers, both of which a student is told to work from"
        def line = sqlFor(engine).firstRow('''SELECT "UnitPrice" AS p, "Quantity" AS q
                                              FROM "Order Details"
                                              WHERE "OrderID" = 4 AND "ProductID" = 1''')
        dec(line.p) == dec("18.0000")
        line.q == 12

        and: "NO OTHER OPERATOR PASSES — the blank has exactly one right answer"
        // A scalar shouldReturn only checks the first cell, so this is what stops + - / from
        // quietly satisfying the koan.
        dec(line.p) + dec(line.q) != dec("216.0000")
        dec(line.p) - dec(line.q) != dec("216.0000")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 3: the three dearest order lines are one product at one price"() {
        given: "the koan's solved form"
        def rows = sqlFor(engine).rows('''SELECT "OrderID", "UnitPrice" * "Quantity" AS "Line total"
                                          FROM "Order Details"
                                          ORDER BY "Line total" DESC, "OrderID"
                                          LIMIT 3''')

        expect:
        rows*.OrderID == [25, 45, 65]
        rows.collect { dec(it."Line total") }.toSet() == [dec("2352.0100")].toSet()

        and: "THE TIE IS REAL, which is why the koan carries a second sort key — episode 15's"
        and: "lesson, still earning its keep three episodes later"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Order Details"
                                   WHERE "UnitPrice" * "Quantity" = 2352.0100''').n == 3

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 4: order 10's two lines, and one of them rounds UP"() {
        given: "the koan's solved form"
        def rows = sqlFor(engine).rows('''SELECT ROUND("UnitPrice" * "Quantity" * (1 - "Discount"), 2) AS "Net total"
                                          FROM "Order Details"
                                          WHERE "OrderID" = 10
                                          ORDER BY "ProductID"''')

        expect:
        rows.collect { dec(it."Net total") } == [dec("136.00"), dec("59.38")]

        and: "the hint's claim: unrounded, the second one is 59.375 — eight decimal places"
        sqlFor(engine).firstRow('''SELECT "UnitPrice" * "Quantity" * (1 - "Discount") AS v
                                   FROM "Order Details"
                                   WHERE "OrderID" = 10 AND "ProductID" = 8''').v.toString() ==
                "59.37500000"

        and: "NO OTHER DIGIT COUNT PASSES — 1 and 3 both give a different second row"
        dec(sqlFor(engine).firstRow('SELECT ROUND(59.375, 3) AS r').r) != dec("59.38")
        dec(sqlFor(engine).firstRow('SELECT ROUND(59.375, 1) AS r').r) != dec("59.38")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koans 5 and 6: text joined end to end, spaces and all"() {
        expect: "koan 5 — town and country as one column"
        sqlFor(engine).rows('''SELECT "City" || ', ' || "Country" AS "Based in"
                               FROM "Suppliers" ORDER BY "CompanyName" LIMIT 3''')*."Based in" ==
                ["London, UK", "Ann Arbor, USA", "New Orleans, USA"]

        and: "koan 6 — the separator is a string of its own, and its spaces are the answer"
        sqlFor(engine).rows('''SELECT "ContactName" || ' at ' || "CompanyName" AS "Who to call"
                               FROM "Suppliers" ORDER BY "ContactName" LIMIT 2''')*."Who to call" ==
                ["Charlotte Cooper at Exotic Liquids", "Giovanni Giudici at Pasta Buttini s.r.l."]

        and: "the hint's warning is true: no space, no error, and the whole line closes up"
        sqlFor(engine).firstRow('''SELECT "ContactName" || 'at' || "CompanyName" AS c
                                   FROM "Suppliers" WHERE "SupplierID" = 1''').c ==
                "Charlotte CooperatExotic Liquids"

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 7: six suppliers, five countries, and the doubled one is the USA"() {
        given: "the koan's solved form"
        def rows = sqlFor(engine).rows('''SELECT DISTINCT "Country" FROM "Suppliers"
                                          ORDER BY "Country"''')

        expect:
        rows*.Country == ["Australia", "Italy", "Japan", "UK", "USA"]

        and: "WITHOUT the blank there are six rows, so the koan cannot pass empty-handed"
        sqlFor(engine).rows('SELECT "Country" FROM "Suppliers"').size() == 6

        and: "the hint's claim about WHICH country is doubled"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Suppliers"
                                   WHERE "Country" = 'USA' ''').n == 2

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 8: the broken 'discount rates' report really does return 155 rows"() {
        expect: "the koan's solved form — three rates, which is the honest answer"
        sqlFor(engine).rows('''SELECT DISTINCT "Discount" FROM "Order Details"
                               ORDER BY "Discount"''').collect { dec(it.Discount) } ==
                ["0.0000", "0.0500", "0.1000"].collect { dec(it) }

        and: "AND THE NUMBER IN THE KOAN'S COMMENT — a wrong one here would tell a student"
        and: "their correct diagnosis was wrong, so it is checked rather than estimated"
        sqlFor(engine).rows('''SELECT DISTINCT "Discount", "OrderID" FROM "Order Details"''').size() == 155

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 9: the same shape on purpose — six rows, and six is right this time"() {
        given: "the koan's solved form"
        def rows = sqlFor(engine).rows('''SELECT DISTINCT "Country", "City" FROM "Suppliers"
                                          ORDER BY "Country", "City"''')

        expect:
        rows.collect { [it.Country, it.City] } ==
                [["Australia", "Melbourne"], ["Italy", "Salerno"], ["Japan", "Tokyo"],
                 ["UK", "London"], ["USA", "Ann Arbor"], ["USA", "New Orleans"]]

        and: "the hint's reason: the two American suppliers really are in two different towns"
        sqlFor(engine).rows('''SELECT "City" FROM "Suppliers" WHERE "Country" = 'USA'
                               ORDER BY "City"''')*.City == ["Ann Arbor", "New Orleans"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 10: order 11 priced up, dearest line first"() {
        given: "the whole query the student writes from scratch"
        def rows = sqlFor(engine).rows('''SELECT "ProductID" AS "Product",
                                                 ROUND("UnitPrice" * "Quantity" * (1 - "Discount"), 2) AS "Line total"
                                          FROM "Order Details"
                                          WHERE "OrderID" = 11
                                          ORDER BY "Line total" DESC''')

        expect:
        rows*.Product == [12, 11, 10]
        rows.collect { dec(it."Line total") } == ["823.20", "552.90", "35.00"].collect { dec(it) }

        and: "order 11 has exactly three lines, so a missing WHERE is visibly wrong"
        rows.size() == 3

        and: "and no two of them tie, so the sort has one right answer"
        rows.collect { dec(it."Line total") }.toSet().size() == 3

        where:
        engine << ENGINES
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/20-distinct-aliases-expressions/scripts/${name}.sql").text
    }

    /** The column headings the engine actually hands back — which is the whole subject of this
     *  lesson, so it is asserted rather than assumed. Reads the JDBC column LABEL, so an alias
     *  shows up here exactly as a learner sees it in CloudBeaver. */
    private List<String> labels(String engine, String sql) {
        sqlFor(engine).rows(sql).first().keySet().toList()*.toString()
    }

    /** Prices are DECIMAL(19,4) on DuckDB and numeric on PostgreSQL, and the two hand back
     *  different Java types with different scales. Compare by VALUE, never by toString or by ==,
     *  or 19.76 and 19.7600 stop being equal for reasons that have nothing to teach.
     *  (The one place this file DOES compare toString is the six-decimal slide, where the number
     *  of decimal places IS the assertion — see the comment there.) */
    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }
}
