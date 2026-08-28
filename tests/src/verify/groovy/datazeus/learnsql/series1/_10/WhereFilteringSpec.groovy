package datazeus.learnsql.series1._10

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _10 "WHERE, AND/OR/NOT, IN, BETWEEN".
 * These are the real answers the blog + video show. The lesson runs FOURTEEN queries and
 * every one of them is asserted here, on both engines:
 *
 *    1. customers-germany           — WHERE equality: 11 of the 25 customers are German
 *    2. customers-germany-lowercase — text comparison is EXACT: 'germany' matches 0 rows
 *    3. customers-not-germany       — <> (same as !=): the other 14 customers
 *    4. products-under-ten          — numbers compare without quotes: 2 products under 10
 *    5. products-cheap-beverages    — AND narrows: beverages under 10 = 1 row (Guarana)
 *    6. products-trap-no-parens     — THE TRAP: AND binds before OR → 4 rows, 2 over the cap
 *    7. products-parens-fix         — parentheses fix it → 2 rows, all under 15
 *    8. products-not-bev-cond       — NOT (...) flips a whole test: 14 of 20 products
 *    9. customers-in-list           — IN: one test, a list of values → 5 customers
 *   10. customers-not-in-list       — NOT IN: the remaining 20
 *   11. products-between            — BETWEEN includes BOTH ends: 9 rows, 10.0000 in
 *   12. products-strict-range       — > and < exclude the ends: 8 rows, 10.0000 out
 *   13. orders-june-2024            — the half-open date habit: >= first AND < next = 4
 *   14. products-like-ch            — LIKE 'Ch%': 3 products start with Ch
 *
 * PLUS three claims the lesson makes that no script covers, and which were unproved until
 * 2026-08-28: that "Products" really holds 20 rows (the denominator behind "14 of the 20"
 * and "two of the twenty"), that the ELEVEN German companies on screen are exactly the right
 * eleven and not just the first and last, and that LIKE is case-exact so a lowercase 'ch%'
 * matches nothing.
 *
 * The learner-facing version, with the queries blanked to ___, is WhereFilteringKoans —
 * THIRTEEN koans, which practise the same ideas in the same order on different questions.
 *
 * Convention: the spec runs the SAME *.sql files the lesson/video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 */
class WhereFilteringSpec extends NorthwindGateSpec {

    // --- 1. WHERE equality: keep only the rows that pass the test ------------------------

    @Unroll
    def "[#engine] the table holds 25 customers, and WHERE Country = Germany keeps 11"() {
        given:
        def sql = sqlFor(engine)

        expect: "the whole table is twenty-five customers"
        sql.firstRow('SELECT count(*) AS n FROM "Customers"').n == 25

        and: "the filter keeps the eleven German ones"
        sql.rows(script("customers-germany")).size() == 11

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the German list runs from Alfreds Futterkiste in Berlin to Die Wandernde Kuh in Stuttgart"() {
        given:
        def rows = sqlFor(engine).rows(script("customers-germany"))

        expect: "two columns per row — the query names CompanyName and City"
        rows.first().size() == 2

        and:
        rows.first().CompanyName == "Alfreds Futterkiste"
        rows.first().City == "Berlin"
        rows.last().CompanyName == "Die Wandernde Kuh"
        rows.last().City == "Stuttgart"

        where:
        engine << ENGINES
    }

    // ALL ELEVEN, not just the ends. The video puts every one of these on screen, so every
    // one of them is a claim. Compared as a SET: row ORDER is not guaranteed without an
    // ORDER BY, and this lesson has not taught one yet.
    @Unroll
    def "[#engine] the eleven German customers are exactly the eleven the lesson lists"() {
        expect:
        sqlFor(engine).rows(script("customers-germany"))*.CompanyName.toSet() == [
                "Alfreds Futterkiste", "Blauer See Delikatessen", "Drachenblut Delikatessen",
                "Frankenversand", "Königlich Essen", "Lehmanns Marktstand",
                "Morgenstern Gesundkost", "Ottilies Käseladen", "QUICK-Stop",
                "Toms Spezialitäten", "Die Wandernde Kuh",
        ].toSet()

        where:
        engine << ENGINES
    }

    // --- 2. Text comparison is EXACT — capitals included ---------------------------------

    @Unroll
    def "[#engine] 'germany' in lowercase matches nothing — zero rows, and no error"() {
        expect:
        sqlFor(engine).rows(script("customers-germany-lowercase")).isEmpty()

        where:
        engine << ENGINES
    }

    // --- 3. Not equal: <> and != are the same operator -----------------------------------

    @Unroll
    def "[#engine] Country <> Germany keeps the other 14 customers"() {
        expect:
        sqlFor(engine).rows(script("customers-not-germany")).size() == 14

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] != returns exactly what <> returns — two spellings, one operator"() {
        expect:
        sqlFor(engine).firstRow(
                'SELECT count(*) AS n FROM "Customers" WHERE "Country" != \'Germany\'').n == 14

        where:
        engine << ENGINES
    }

    // --- 4. Numbers compare without quotes ------------------------------------------------

    @Unroll
    def "[#engine] two products cost less than 10 — Filo Mix and Guarana Fantastica"() {
        given:
        def rows = sqlFor(engine).rows(script("products-under-ten"))

        expect:
        rows.size() == 2
        rows*.ProductName == ["Filo Mix", "Guarana Fantastica"]

        // THE STORED VALUE, which is what JDBC hands back and what this asserts: "UnitPrice"
        // is DECIMAL(19,4), so every price carries four decimal places. The LESSON and the
        // VIDEO both show 7 and 4.5, because CloudBeaver trims the trailing zeros and
        // CloudBeaver is the client they tell the learner to open. Same value, different
        // rendering — episode 07 explains it, and this line is not in conflict with them.
        and: "the prices carry the type's four decimals"
        rows*.UnitPrice*.toString() == ["7.0000", "4.5000"]

        where:
        engine << ENGINES
    }

    // --- 5. AND narrows: every condition must pass ----------------------------------------

    @Unroll
    def "[#engine] beverages AND under 10 leaves exactly one row — Guarana Fantastica"() {
        given:
        def rows = sqlFor(engine).rows(script("products-cheap-beverages"))

        expect:
        rows.size() == 1
        rows.first().ProductName == "Guarana Fantastica"

        where:
        engine << ENGINES
    }

    // --- 6+7. The precedence trap, then the parentheses fix -------------------------------

    @Unroll
    def "[#engine] without parentheses AND grabs first: 4 rows, and Chai at 18.0000 sneaks past the 15 cap"() {
        given:
        def rows = sqlFor(engine).rows(script("products-trap-no-parens"))

        expect: "four rows — the query silently became: beverages, OR (condiments under 15)"
        rows.size() == 4

        and: "the proof of the bug: two of them are over the price cap the query meant to set"
        rows*.ProductName.containsAll(["Chai", "Chang"])
        rows.count { (it.UnitPrice as BigDecimal) > 15 } == 2

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] with parentheses the OR happens first: 2 rows, every price under 15"() {
        given:
        def rows = sqlFor(engine).rows(script("products-parens-fix"))

        expect: "two rows — order-free compare: an OR lets the engine pick its scan order"
        rows.size() == 2
        rows*.ProductName.toSet() == ["Aniseed Syrup", "Guarana Fantastica"].toSet()

        and: "no row breaks the cap this time"
        rows.every { (it.UnitPrice as BigDecimal) < 15 }

        where:
        engine << ENGINES
    }

    // --- 8. NOT flips a whole (parenthesized) test ----------------------------------------

    // THE DENOMINATOR. The lesson says 'fourteen of the twenty' and 'only two of the twenty
    // products' - both are claims about the size of the table, and neither was proved.
    @Unroll
    def "[#engine] the Products table holds 20 rows, the denominator the lesson quotes"() {
        expect:
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] NOT (beverages OR condiments) keeps the other 14 of the 20 products"() {
        expect:
        sqlFor(engine).firstRow(script("products-not-bev-cond"))[0] == 14

        where:
        engine << ENGINES
    }

    // --- 9+10. IN and NOT IN ---------------------------------------------------------------

    @Unroll
    def "[#engine] IN with three countries keeps five customers"() {
        given:
        def rows = sqlFor(engine).rows(script("customers-in-list"))

        expect:
        rows.size() == 5

        and: "two Mexican, two Venezuelan, one Argentinian"
        rows.count { it.Country == "Mexico" } == 2
        rows.count { it.Country == "Venezuela" } == 2
        rows.count { it.Country == "Argentina" } == 1

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] NOT IN keeps the remaining twenty — 25 minus the 5 the list named"() {
        expect:
        sqlFor(engine).firstRow(script("customers-not-in-list"))[0] == 20

        where:
        engine << ENGINES
    }

    // --- 11+12. BETWEEN includes both ends; > < excludes them ------------------------------

    @Unroll
    def "[#engine] BETWEEN 10 AND 20 keeps nine products — including Aniseed Syrup at exactly 10.0000"() {
        given:
        def rows = sqlFor(engine).rows(script("products-between"))

        expect:
        rows.size() == 9

        and: "the row sitting exactly ON the lower end is in — BETWEEN means >= AND <="
        rows.any { it.ProductName == "Aniseed Syrup" && it.UnitPrice.toString() == "10.0000" }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the same range with > and < drops the row on the boundary: eight remain"() {
        given:
        def rows = sqlFor(engine).rows(script("products-strict-range"))

        expect:
        rows.size() == 8

        and: "Aniseed Syrup is the one that vanished"
        !rows*.ProductName.contains("Aniseed Syrup")

        where:
        engine << ENGINES
    }

    // --- 13. The date habit: half-open ranges ----------------------------------------------

    @Unroll
    def "[#engine] June 2024 orders via >= first AND < next month — the episode 00 answer, 4"() {
        expect:
        sqlFor(engine).firstRow(script("orders-june-2024"))[0] == 4

        where:
        engine << ENGINES
    }

    // On THIS data BETWEEN two dates also returns 4 (every order lands at midnight), which is
    // exactly why the lesson teaches the habit instead of the coincidence: an order stamped
    // 2024-06-30 14:00 would slip PAST 'BETWEEN ... AND DATE 2024-06-30' but not past '< 07-01'.
    @Unroll
    def "[#engine] BETWEEN two dates happens to agree here — the lesson explains why that is luck"() {
        expect:
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" ' +
                "WHERE \"OrderDate\" BETWEEN DATE '2024-06-01' AND DATE '2024-06-30'").n == 4

        where:
        engine << ENGINES
    }

    // --- 14. LIKE patterns -------------------------------------------------------------------

    @Unroll
    def "[#engine] LIKE 'Ch%' finds the three products that start with Ch"() {
        expect:
        sqlFor(engine).rows(script("products-like-ch"))*.ProductName ==
                ["Chai", "Chang", "Chef Antons Cajun Seasoning"]

        where:
        engine << ENGINES
    }

    // CASE-EXACT, which Leo asserts on screen (lowercase ch-percent would find nothing).
    // Same rule as the 'germany' case above, on the other text operator.
    @Unroll
    def "[#engine] LIKE is case-exact: lowercase ch% matches nothing"() {
        expect:
        sqlFor(engine).rows('SELECT "ProductName" FROM "Products" WHERE "ProductName" LIKE \'ch%\'').isEmpty()

        where:
        engine << ENGINES
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/10-where-filtering/scripts/${name}.sql").text
    }
}
