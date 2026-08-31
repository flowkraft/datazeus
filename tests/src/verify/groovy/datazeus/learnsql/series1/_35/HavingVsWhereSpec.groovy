package datazeus.learnsql.series1._35

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _35 "HAVING vs WHERE".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted
 * here, on BOTH engines. The lesson's ten scripts:
 *
 *    1. countries-over-five-orders      — the payoff episode 30 promised: SIX countries
 *    2. aggregate-in-where-fails        — the refusal, recapped from 30 (42803 / Binder Error)
 *    3. expensive-orders-busy-countries — WHERE and HAVING in one query, each doing its job
 *    4. cheap-to-ship-wrong             — THE TRAP: nine countries, every row under fifty
 *    5. dearest-delivery-per-country    — the evidence that the trap lied (Germany: 95.75)
 *    6. cheap-to-ship-right             — the fix, and the answer is ONE country
 *    7. freight-bill-over-300           — filtering on an aggregate the report never shows
 *    8. country-freight-totals          — the totals behind script 7, so the absence is provable
 *    9. alias-in-having-fails           — THE ENGINE SPLIT: runs on DuckDB, 42703 on PostgreSQL
 *   10. alias-in-having-portable        — the same question, written so both engines agree
 *
 * ── WHAT IS DELIBERATELY ASSERTED PER ENGINE, AND WHY ───────────────────────────────────
 *
 *   THE ALIAS (scripts 9 and 10). `HAVING "OrderCount" > 5` — filtering on the name given in
 *   the SELECT — RUNS ON DUCKDB and is REFUSED BY POSTGRESQL with 42703, column "OrderCount"
 *   does not exist. Both are defensible: HAVING is evaluated before SELECT, so PostgreSQL is
 *   following the standard and DuckDB is being generous. THAT DIFFERENCE IS THE SLIDE, so it
 *   is asserted per engine rather than smoothed over — and then script 10 is asserted
 *   IDENTICAL on both, which is what turns "write the aggregate out again and it works
 *   everywhere" from a nice sentence into something this gate actually proves.
 *
 *   THE TWO ERRORS (scripts 2 and 9) are asserted only as REFUSALS, not by message text. The
 *   wording differs per engine (DuckDB: "Binder Error: WHERE clause cannot contain
 *   aggregates!"; PostgreSQL: "aggregate functions are not allowed in WHERE"), and only the
 *   refusal is portable. The exact PostgreSQL strings and SQLSTATEs the VIDEO draws in its
 *   ErrorPanel are pinned separately, against PostgreSQL alone, and marked as a recording.
 *
 * ── WHAT IS NOT HERE, ON PURPOSE ────────────────────────────────────────────────────────
 *
 *   NO avg() ANYWHERE. It was the first choice for the trap and it is barred from this whole
 *   lesson: DuckDB returns a DOUBLE (54.626666666666665) where PostgreSQL returns a NUMERIC
 *   (54.6266666666666667), so any average with a repeating decimal disagrees between the
 *   engine on the slide and the engine in the learner's CloudBeaver. count/sum/max over
 *   DECIMAL(19,4) are byte-identical on both, so the lesson is built on those three.
 *   Measured on both engines 2026-08-30.
 *
 *   NOTHING IS PINNED TO AN UNORDERED RESULT. GROUP BY promises one row per group and says
 *   nothing about their order, so every multi-row script carries an ORDER BY and every table
 *   asserted below is deterministic on both engines. Where a script has no ORDER BY it is
 *   because it returns exactly one row.
 *
 * Convention: the spec runs the SAME *.sql files the lesson and the video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 *
 * AND THEN THE KOANS, ALL TEN, in their own section at the bottom. They deliberately do NOT
 * reuse the lesson's queries — the lesson counts ORDERS per country, the koans work in the
 * warehouse over "Products" — so none of the assertions above touches the data they stand on.
 * Every koan is checked in its solved form on both engines, plus the factual claims their
 * HINTS make, because a wrong number in a hint tells a student their correct query is wrong.
 */
class HavingVsWhereSpec extends NorthwindGateSpec {

    // --- 1. The payoff episode 30 promised out loud ----------------------------------------

    @Unroll
    def "[#engine] HAVING keeps six of the ten countries — the list episode 30 promised"() {
        given: "the first query of the lesson: countries with more than five orders"
        def rows = sqlFor(engine).rows(script("countries-over-five-orders"))

        expect: "SIX, and exactly these — episode 30's where-cant-count slide says 'six of our"
        and: "countries would come back', so if this list ever changes size that slide is a lie"
        rows*.ShipCountry == ["Germany", "Mexico", "Sweden", "UK", "France", "Venezuela"]
        rows*.OrderCount == [32, 8, 8, 7, 6, 6]

        and: "ten countries exist, so the filter really did remove four of them"
        sqlFor(engine).firstRow('SELECT count(DISTINCT "ShipCountry") AS n FROM "Orders"').n == 10

        and: "THE UK IS BACK, and that is the point of the highlight on the having-result slide."
        and: "In episode 30 the UK VANISHED from a report (WHERE Freight > 50 killed all seven"
        and: "of its orders before grouping). Nothing is filtered before counting here, so it"
        and: "returns — with the seven orders 30 said it had."
        rows.find { it.ShipCountry == "UK" }.OrderCount == 7

        where:
        engine << ENGINES
    }

    // --- 2. The refusal, recapped from episode 30 ------------------------------------------

    @Unroll
    def "[#engine] an aggregate in the WHERE is REFUSED — there is no count yet at step two"() {
        when: "asking for count(*) > 5 in the WHERE, which is what a learner tries first"
        sqlFor(engine).rows(script("aggregate-in-where-fails"))

        then: "both engines refuse; only the refusal is portable, the wording is not"
        // DuckDB:     "Binder Error: WHERE clause cannot contain aggregates!"
        // PostgreSQL: "aggregate functions are not allowed in WHERE" (SQLSTATE 42803)
        thrown(Exception)

        where:
        engine << ENGINES
    }

    def "the ErrorPanel on why-where-fails is what PostgreSQL really says"() {
        // A RECORDING, NOT A RULE — pinned to PostgreSQL alone because that is the engine
        // CloudBeaver talks to, and CloudBeaver is the client this course tells learners to
        // open. The video draws this SQLSTATE and this message verbatim.
        when:
        sqlFor("postgres").rows(script("aggregate-in-where-fails"))

        then:
        def e = thrown(Exception)
        rootMessage(e).contains("aggregate functions are not allowed in WHERE")
    }

    // --- 3. Both filters in one query ------------------------------------------------------

    @Unroll
    def "[#engine] WHERE and HAVING cooperate: four countries clear both bars"() {
        given: "expensive deliveries only, then only the countries with more than two of them"
        def rows = sqlFor(engine).rows(script("expensive-orders-busy-countries"))

        expect:
        rows*.ShipCountry == ["Germany", "France", "Austria", "Sweden"]
        rows*.OrderCount == [20, 4, 3, 3]

        and: "THE TIE AT 3 IS REAL, which is why the script carries a second sort key —"
        and: "lesson 15's rule, and grouped rows are not exempt from it"
        rows.findAll { it.OrderCount == 3 }*.ShipCountry.toSet() == ["Austria", "Sweden"].toSet()

        where:
        engine << ENGINES
    }

    // --- 4. THE TRAP -----------------------------------------------------------------------

    @Unroll
    def "[#engine] THE TRAP: filtering the rows first returns nine countries, all under fifty"() {
        given: "WHERE Freight <= 50, then group — the query Leo writes and the video shows"
        def rows = sqlFor(engine).rows(script("cheap-to-ship-wrong"))

        expect: "NINE rows, and this exact table is on screen"
        rows*.ShipCountry == ["Argentina", "France", "Germany", "Italy", "Mexico",
                              "Sweden", "UK", "USA", "Venezuela"]
        rows*.Dearest.collect { dec(it) } == ["49.71", "47.93", "45.15", "48.32", "24.06",
                                              "40.00", "45.50", "11.39", "46.54"].collect { dec(it) }

        and: "THE REPORT CONFIRMS ITSELF — every single row satisfies the question that was"
        and: "asked, which is exactly why this bug survives code review. Stated as a property"
        and: "rather than by re-listing the numbers, because the property IS the lesson."
        rows.every { dec(it.Dearest) <= dec("50") }

        and: "AUSTRIA IS NOT HERE AT ALL — all three of its orders cost more than 50 to ship,"
        and: "so WHERE removed every one of them and the group never existed. It is absent,"
        and: "not zero. Leo says this out loud on the proof-result slide."
        !rows*.ShipCountry.contains("Austria")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] and the trap LIED: Germany's dearest delivery is really 95.75, not 45.15"() {
        given: "the same table with nothing filtered out — the proof-result slide"
        def rows = sqlFor(engine).rows(script("dearest-delivery-per-country"))

        expect:
        rows*.ShipCountry == ["Venezuela", "Mexico", "USA", "Germany", "Sweden"]
        rows*.Dearest.collect { dec(it) } ==
                ["98.92", "97.53", "96.14", "95.75", "87.33"].collect { dec(it) }

        and: "THE CONTRADICTION, asserted as one fact so it cannot rot: the trap reported"
        and: "45.15 for Germany and the truth is 95.75. Both come out of the same column of"
        and: "the same table. This single pair is the centre of the episode."
        dec(rows.find { it.ShipCountry == "Germany" }.Dearest) == dec("95.75")
        dec(sqlFor(engine).rows(script("cheap-to-ship-wrong"))
                .find { it.ShipCountry == "Germany" }.Dearest) == dec("45.15")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] THE FIX: asking after the piles are built answers ONE country, the UK"() {
        given: "HAVING max(Freight) <= 50 — the same condition, moved from step two to step four"
        def rows = sqlFor(engine).rows(script("cheap-to-ship-right"))

        expect: "one row, and it is the UK at 45.50 — the same figure episode 30 used when the"
        and: "UK vanished from ITS report. Same number, opposite direction, one lesson apart."
        rows.size() == 1
        rows[0].ShipCountry == "UK"
        dec(rows[0].Dearest) == dec("45.50")

        and: "NINE VERSUS ONE — the wrong answer is not slightly wrong, it is the opposite one"
        sqlFor(engine).rows(script("cheap-to-ship-wrong")).size() == 9

        where:
        engine << ENGINES
    }

    // --- 5. Filtering on a number the report never shows ------------------------------------

    @Unroll
    def "[#engine] HAVING can filter on an aggregate that is nowhere in the SELECT"() {
        given: "orders per country, keeping only the countries whose freight bill tops 300"
        def rows = sqlFor(engine).rows(script("freight-bill-over-300"))

        expect:
        rows*.ShipCountry == ["France", "Germany", "Sweden"]
        rows*.OrderCount == [6, 32, 8]

        and: "THE SLIDE'S WHOLE POINT, and it is checkable: France and Venezuela have the SAME"
        and: "order count, one is in and one is out, and nothing on the page explains it."
        rows.find { it.ShipCountry == "France" }.OrderCount == 6
        !rows*.ShipCountry.contains("Venezuela")
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShipCountry" = 'Venezuela' ''').n == 6

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the invisible column, made visible: France 347.85 clears 300, Venezuela 254.38 does not"() {
        given: "the totals the previous query filtered on but never printed"
        def rows = sqlFor(engine).rows(script("country-freight-totals"))

        expect:
        rows*.ShipCountry == ["Germany", "Sweden", "France", "Venezuela", "Austria"]
        rows*.TotalFreight.collect { dec(it) } ==
                ["1841.78", "410.60", "347.85", "254.38", "226.15"].collect { dec(it) }

        and: "so the absence on the previous slide has a cause, and it is this number"
        dec(rows.find { it.ShipCountry == "France" }.TotalFreight) > dec("300")
        dec(rows.find { it.ShipCountry == "Venezuela" }.TotalFreight) < dec("300")

        where:
        engine << ENGINES
    }

    // --- 6. THE ENGINE SPLIT — asserted per engine, on purpose ------------------------------

    def "the alias in a HAVING RUNS on DuckDB — which is the dangerous half"() {
        // DuckDB resolves the SELECT alias inside HAVING as a convenience. It is not wrong,
        // it is generous — and it is why this bug reaches production: the query works on the
        // developer's machine and fails on the server.
        given:
        def rows = sqlFor("duckdb").rows(script("alias-in-having-fails"))

        expect:
        rows*.ShipCountry == ["France", "Germany", "Mexico", "Sweden", "UK", "Venezuela"]
        rows*.OrderCount == [6, 32, 8, 8, 7, 6]
    }

    def "the same alias is REFUSED by PostgreSQL with 42703 — the ErrorPanel on alias-fails"() {
        // HAVING is evaluated BEFORE SELECT, so at that moment "OrderCount" has not been
        // created. PostgreSQL is following the standard. The video draws this message.
        when:
        sqlFor("postgres").rows(script("alias-in-having-fails"))

        then:
        def e = thrown(Exception)
        rootMessage(e).contains('column "OrderCount" does not exist')
    }

    @Unroll
    def "[#engine] and writing the aggregate out again makes the two engines agree"() {
        given: "the portable form — count(*) repeated in the HAVING instead of the alias"
        def rows = sqlFor(engine).rows(script("alias-in-having-portable"))

        expect: "IDENTICAL on both engines, which is what makes the lesson's advice provable"
        rows*.ShipCountry == ["France", "Germany", "Mexico", "Sweden", "UK", "Venezuela"]
        rows*.OrderCount == [6, 32, 8, 8, 7, 6]

        and: "and it is the same six countries as the lesson's opening query, only re-sorted"
        rows*.ShipCountry.toSet() ==
                sqlFor(engine).rows(script("countries-over-five-orders"))*.ShipCountry.toSet()

        where:
        engine << ENGINES
    }

    // --- What the KOANS stand on ------------------------------------------------------------
    //
    // All ten, in their SOLVED form, on both engines — plus every factual claim a koan's
    // HINT makes, because a wrong number in a hint tells a student their correct query is
    // wrong and there is nothing they can do about it.
    // These run over "Products" and "Customers"; the lesson above runs over "Orders". No
    // assertion in this section shares data with any assertion above, which is the point.

    @Unroll
    def "[#engine] koan 1 — HAVING count(*) > 3 keeps two suppliers"() {
        expect:
        sqlFor(engine).rows('''SELECT "SupplierID", count(*) AS "n" FROM "Products"
                               GROUP BY "SupplierID" HAVING count(*) > 3
                               ORDER BY "SupplierID"''')
                .collect { [it.SupplierID, it.n] } == [[4, 5], [6, 4]]

        and: "the hint says six suppliers stock 20 products between them"
        sqlFor(engine).firstRow('SELECT count(DISTINCT "SupplierID") AS n FROM "Products"').n == 6
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 2 — min(UnitPrice) > 20 keeps two categories"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT "CategoryID", min("UnitPrice") AS "p"
                                          FROM "Products" GROUP BY "CategoryID"
                                          HAVING min("UnitPrice") > 20 ORDER BY "CategoryID"''')

        expect:
        rows*.CategoryID == [6, 7]
        rows*.p.collect { dec(it) } == [dec("97.00"), dec("23.25")]

        and: "the hint warns that max would let a category through on one dear line, and the"
        and: "gap is real: SIX categories have a max over 20, against these TWO on min."
        and: "(Written as 'eight' first, from memory rather than from the database, and the"
        and: "gate caught it — categories 1 and 3 top out at 19.00 and 17.45. Run the query.)"
        sqlFor(engine).rows('''SELECT "CategoryID" FROM "Products" GROUP BY "CategoryID"
                               HAVING max("UnitPrice") > 20''').size() == 6

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 3 — WHERE not-discontinued, then HAVING count >= 3: four suppliers"() {
        expect:
        sqlFor(engine).rows('''SELECT "SupplierID", count(*) AS "n" FROM "Products"
                               WHERE "Discontinued" = false GROUP BY "SupplierID"
                               HAVING count(*) >= 3 ORDER BY "SupplierID"''')
                .collect { [it.SupplierID, it.n] } == [[1, 3], [3, 3], [4, 4], [6, 4]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 4 — HAVING sum(UnitsInStock) > 100 keeps two categories"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT "CategoryID", count(*) AS "n" FROM "Products"
                                          GROUP BY "CategoryID"
                                          HAVING sum("UnitsInStock") > 100
                                          ORDER BY "CategoryID"''')

        expect:
        rows.collect { [it.CategoryID, it.n] } == [[2, 3], [8, 2]]

        and: "the hint says one of them holds the most stock on only two products — category 8,"
        and: "154 units across 2 products, against category 2's 105 across 3"
        sqlFor(engine).firstRow('''SELECT sum("UnitsInStock") AS s FROM "Products"
                                   WHERE "CategoryID" = 8''').s == 154

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 5 — THE TRAP in the warehouse: four suppliers, not six"() {
        given: "the solved form — HAVING max(UnitPrice) <= 50"
        def right = sqlFor(engine).rows('''SELECT "SupplierID", max("UnitPrice") AS "p"
                                           FROM "Products" GROUP BY "SupplierID"
                                           HAVING max("UnitPrice") <= 50
                                           ORDER BY "SupplierID"''')

        expect:
        right*.SupplierID == [1, 2, 3, 6]
        right*.p.collect { dec(it) } == ["19.00", "22.00", "30.00", "38.00"].collect { dec(it) }

        and: "THE HINT'S CLAIMS, all three of them. 'It gives you SIX suppliers — every"
        and: "supplier we have', and 'supplier 5 sells something at 123.79'."
        sqlFor(engine).rows('''SELECT "SupplierID" FROM "Products" WHERE "UnitPrice" <= 50
                               GROUP BY "SupplierID"''').size() == 6
        sqlFor(engine).firstRow('SELECT count(DISTINCT "SupplierID") AS n FROM "Products"').n == 6
        dec(sqlFor(engine).firstRow('''SELECT max("UnitPrice") AS p FROM "Products"
                                       WHERE "SupplierID" = 5''').p) == dec("123.79")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 6 — the predicted counts really are 6 and 4"() {
        expect:
        sqlFor(engine).rows('''SELECT "SupplierID", max("UnitPrice") FROM "Products"
                               WHERE "UnitPrice" <= 50 GROUP BY "SupplierID"''').size() == 6
        sqlFor(engine).rows('''SELECT "SupplierID", max("UnitPrice") FROM "Products"
                               GROUP BY "SupplierID"
                               HAVING max("UnitPrice") <= 50''').size() == 4

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 7 — WHERE first drops category 6 entirely: seven rows, not eight"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT "CategoryID", count(*) AS "n" FROM "Products"
                                          WHERE "UnitPrice" < 50 GROUP BY "CategoryID"
                                          ORDER BY "CategoryID"''')

        expect:
        rows.collect { [it.CategoryID, it.n] } ==
                [[1, 3], [2, 3], [3, 2], [4, 3], [5, 3], [7, 2], [8, 2]]

        and: "THE HINT'S CLAIM: there are eight categories, and category 6 is missing because"
        and: "BOTH of its products cost more than 50 — absent, not zero"
        sqlFor(engine).firstRow('SELECT count(DISTINCT "CategoryID") AS n FROM "Products"').n == 8
        !rows*.CategoryID.contains(6)
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Products"
                                   WHERE "CategoryID" = 6 AND "UnitPrice" < 50''').n == 0

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 8 — min(UnitPrice) > 10 keeps three suppliers, and 10.00 is excluded"() {
        given:
        def rows = sqlFor(engine).rows('''SELECT "SupplierID", min("UnitPrice") AS "p"
                                          FROM "Products" GROUP BY "SupplierID"
                                          HAVING min("UnitPrice") > 10 ORDER BY "SupplierID"''')

        expect:
        rows*.SupplierID == [3, 5, 6]
        rows*.p.collect { dec(it) } == ["12.50", "17.45", "12.50"].collect { dec(it) }

        and: "THE HINT'S CLAIM, and it is what makes the operator matter: supplier 1's cheapest"
        and: "is EXACTLY 10.00, so > excludes them and >= would not"
        dec(sqlFor(engine).firstRow('''SELECT min("UnitPrice") AS p FROM "Products"
                                       WHERE "SupplierID" = 1''').p) == dec("10.00")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 9 — the portable form keeps five suppliers"() {
        expect:
        sqlFor(engine).rows('''SELECT "SupplierID", count(*) AS "ProductCount" FROM "Products"
                               GROUP BY "SupplierID" HAVING count(*) > 2
                               ORDER BY "SupplierID"''')
                .collect { [it.SupplierID, it.ProductCount] } ==
                [[1, 3], [3, 3], [4, 5], [5, 3], [6, 4]]

        where:
        engine << ENGINES
    }

    def "koan 9's whole point: the alias form really does split the two engines"() {
        // The koan tells the student that "ProductCount" would go GREEN on DuckDB and fail on
        // the PostgreSQL in CloudBeaver with 42703. If that stopped being true the koan would
        // be teaching a superstition, so the gate checks both halves.
        given: "the lazy form the koan warns against"
        String lazy = '''SELECT "SupplierID", count(*) AS "ProductCount" FROM "Products"
                         GROUP BY "SupplierID" HAVING "ProductCount" > 2 ORDER BY "SupplierID"'''

        expect: "DuckDB accepts it — and returns the right answer, which is why it is a trap"
        sqlFor("duckdb").rows(lazy).collect { [it.SupplierID, it.ProductCount] } ==
                [[1, 3], [3, 3], [4, 5], [5, 3], [6, 4]]

        when: "PostgreSQL sees the same query"
        sqlFor("postgres").rows(lazy)

        then:
        def e = thrown(Exception)
        rootMessage(e).contains('column "ProductCount" does not exist')
    }

    @Unroll
    def "[#engine] koan 10 — three categories are holding under fifty units"() {
        expect:
        sqlFor(engine).rows('''SELECT "CategoryID", sum("UnitsInStock") AS "s" FROM "Products"
                               GROUP BY "CategoryID" HAVING sum("UnitsInStock") < 50
                               ORDER BY "CategoryID"''')
                .collect { [it.CategoryID, it.s as int] } == [[3, 35], [4, 41], [6, 29]]

        and: "the hint promises one of them is the expensive category met twice already"
        sqlFor(engine).firstRow('''SELECT min("UnitPrice") AS p FROM "Products"
                                   WHERE "CategoryID" = 6''').p != null

        where:
        engine << ENGINES
    }

    // --- helpers ----------------------------------------------------------------------------

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/35-having-vs-where/scripts/${name}.sql").text
    }

    /** Prices and freight are DECIMAL(19,4) on DuckDB and numeric on PostgreSQL, and the two
     *  hand back different Java types with different scales. Compare by VALUE, never by
     *  toString or by ==, or 45.5 and 45.5000 stop being equal for reasons that have nothing
     *  to teach. */
    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }

    /** The driver wraps the engine's complaint, so the text we assert on can be one or two
     *  causes down. Walk to the bottom and join the chain — asserting on the top-level
     *  message alone is how an error assertion quietly stops checking anything. */
    private static String rootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder()
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.message ?: "").append(" | ")
            if (c.getCause() == c) break
        }
        sb.toString()
    }
}
