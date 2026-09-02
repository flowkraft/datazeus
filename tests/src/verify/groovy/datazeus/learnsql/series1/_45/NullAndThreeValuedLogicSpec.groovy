package datazeus.learnsql.series1._45

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _45 "NULL & Three-Valued Logic".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted
 * here, on BOTH engines. The lesson's scripts:
 *
 *    1. backlog-equals-null          — THE OPENING BUG: `= NULL` returns 0 out of 27
 *    2. backlog-is-null              — the fix: IS NULL returns 27
 *    3. backlog-oldest-first         — the backlog itself, oldest first
 *    4. shipped-in-2024              — 18
 *    5. shipped-before-2024          — 34, and 18 + 34 is not 79
 *    6. shipped-not-2024             — wrapping it in NOT changes nothing: still 34
 *    7. shipped-before-or-never      — 61, and 18 + 61 IS 79
 *    8. label-broken                 — one empty piece empties the whole concatenation
 *    9. label-coalesce               — COALESCE puts the line back
 *   10. count-star-vs-column         — 79 rows, 52 values: the loop back to lesson 25
 *   11. avg-days-to-ship             — 5.8, over 52 orders and not 79
 *   12. nullif-in-stock              — NULLIF, COALESCE backwards
 *   13. unshipped-with-customer-names— the hands-on: last lesson's JOIN, this lesson's IS NULL
 *   14. regions-distinct             — article only: DISTINCT keeps ONE null
 *   15. regions-grouped              — article only: GROUP BY makes ONE group of them
 *
 * THIS EPISODE IS A CONTRACT WITH EPISODE 40, and the contract is specific. 40 signs off
 * with "those blanks you made today — now find out what they do to a WHERE, because they do
 * not behave like a value, and testing one with an equals sign quietly matches nothing at
 * all", and its article promises three things by name: that `WHERE "ShippedDate" = NULL`
 * returns nothing, that a NULL is not equal to another NULL, and that COUNT(column) and
 * COUNT(*) "can disagree by a third of your business". All three are asserted below, and the
 * third is literally true here: 79 against 52 is a gap of 27, which is a third of the orders.
 *
 * FOUR OLDER IOUs ARE ALSO PAID, and each was written into a shipped lesson as a promise that
 * Series 1 · 45 would explain it: 07 ("missing values change how comparisons behave"),
 * 10 ("park the two words for now"), 15 ("never write `= NULL` … Series 1 · 45 covers why")
 * and 20 (a `||` with an empty piece goes empty entirely — and 20 names "Region" as the
 * column that would bite). 25 makes the same promise about `<> NULL`.
 *
 * WHY THE DATA IS THE LESSON HERE, and why the NULL INVENTORY below is asserted first. Every
 * claim this episode makes is a claim about how many cells are EMPTY — 27 of 79 order rows
 * have never shipped, 21 of 25 customers have no region. Change one of those and the video
 * does not get a wrong colour, it starts telling a student a false number. So the inventory
 * is pinned before anything that rests on it, and it is the feature that should go red first.
 *
 * ENGINE DIFFERENCES ARE ASSERTED PER ENGINE, NOT AVERAGED AWAY. There is exactly one in
 * this lesson and it is real: an UNGUARDED division by zero stops PostgreSQL dead with
 * SQLSTATE 22012, while DuckDB hands back `inf` without complaint. That is why the article
 * says what it says, and it is also why the video's NULLIF slide does NOT show a division —
 * the guarded result is integer division on PostgreSQL and floating point on DuckDB, so the
 * NUMBER would differ even though the fix is identical. What is portable, and what is
 * asserted, is that the two zero-stock rows come back NULL on both once NULLIF is applied.
 *
 * Convention: the spec runs the SAME *.sql files the lesson and the video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 *
 * AND THEN THE KOANS, ALL TEN, in their own section at the bottom. They deliberately do NOT
 * reuse the lesson's queries — the lesson works on ORDERS that never shipped and CUSTOMERS
 * with no region, the koans work on the SUPPLIER list, the staff list and the shelf — so none
 * of the assertions above touches the data they stand on. Every koan is checked in its solved
 * form, on both engines, plus every factual claim its comment makes to the student.
 */
class NullAndThreeValuedLogicSpec extends NorthwindGateSpec {

    // --- 0. The dataset, and the NULL inventory the whole lesson rests on -------------------

    def "the dataset is the small Northwind the lesson quotes"() {
        // Every "seventy-nine orders", "twenty-five customers", "six suppliers" and "three
        // people" in the article and on the slides resolves to these numbers. This is the
        // SMALL Northwind, not the 91-customer original, so looking an answer up elsewhere
        // gives a different one.
        expect:
        ENGINES.every { engine ->
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers"').n == 25 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Suppliers"').n == 6 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Employees"').n == 3 &&
            sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products"').n == 20
        }
    }

    @Unroll
    def "[#engine] THE NULL INVENTORY — exactly which cells are empty, and how many"() {
        // THE LOAD-BEARING FACT OF THE WHOLE EPISODE, asserted before anything that rests on
        // it. Not one number on a slide survives a change here: 27 IS the backlog, 27 IS the
        // gap between the two counts, 21 IS how many mailing labels vanish, and 52 IS how
        // many orders the average is really computed over. If this feature goes red, the
        // video is lying to somebody — fix the video, never this.
        expect: "the order backlog: 27 of 79 have never shipped, and nothing else is empty"
        nulls(engine, "Orders", "ShippedDate") == 27
        nulls(engine, "Orders", "OrderDate") == 0
        nulls(engine, "Orders", "CustomerID") == 0

        and: "the customer regions: 21 of 25 were never recorded"
        nulls(engine, "Customers", "Region") == 21
        nulls(engine, "Customers", "City") == 0

        and: "the koans' tables: 3 of 6 suppliers have no region, 1 of 3 staff has no manager"
        nulls(engine, "Suppliers", "Region") == 3
        nulls(engine, "Employees", "ReportsTo") == 1

        and: "and 'empty' is NOT 'zero' — UnitsInStock is never empty, but it IS zero twice"
        nulls(engine, "Products", "UnitsInStock") == 0
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "UnitsInStock" = 0').n == 2

        where:
        engine << ENGINES
    }

    // --- 1. Three-valued logic itself — the rules the rest of the lesson is a consequence of

    @Unroll
    def "[#engine] a comparison against NULL is neither true nor false — it is UNKNOWN"() {
        // THE SLIDE THAT NAMES CODD'S THIRD TRUTH VALUE, asserted directly rather than
        // demonstrated through a table, because these are the axioms and everything else in
        // the episode follows from them. Over JDBC an SQL UNKNOWN arrives as a Java null,
        // which is why every one of these reads `== null`.
        //
        // EVERY BARE NULL IS CAST, and that is not decoration. A bare NULL has no type, and
        // PostgreSQL refuses to resolve an operator whose operands are both of unknown type
        // ("operator is not unique"). DuckDB is happy either way, so writing it untyped would
        // pass locally and fail the gate — the exact shape of bug this file exists to catch.
        expect: "an equals sign against NULL cannot answer, whichever side the NULL is on"
        truth(engine, "CAST(NULL AS INTEGER) = CAST(NULL AS INTEGER)") == null
        truth(engine, "CAST(NULL AS INTEGER) <> CAST(NULL AS INTEGER)") == null
        truth(engine, "CAST(NULL AS INTEGER) = 5") == null
        truth(engine, "'Victoria' <> CAST(NULL AS VARCHAR)") == null

        and: "AND SO A NULL IS NOT EQUAL TO ANOTHER NULL — episode 40's article promises this"
        and: "one by name. Two missing values are not known to be the same value."
        truth(engine, "CAST(NULL AS INTEGER) = CAST(NULL AS INTEGER)") != Boolean.TRUE

        and: "NOT does not flip UNKNOWN — the opposite of 'I do not know' is 'I do not know'"
        truth(engine, "NOT (CAST(NULL AS INTEGER) = 5)") == null

        and: "IS NULL, by contrast, always answers — that is the entire reason it exists"
        truth(engine, "CAST(NULL AS INTEGER) IS NULL") == true
        truth(engine, "CAST(NULL AS INTEGER) IS NOT NULL") == false
        truth(engine, "5 IS NOT NULL") == true

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the AND and OR truth tables the video draws"() {
        // The four cells that matter, and the two that are NOT unknown are the interesting
        // ones: OR only needs one TRUE, and AND only needs one FALSE, so in those two cases
        // the missing value cannot change the answer and the database says so. `OR ... IS
        // NULL` works precisely because of the first line here.
        expect:
        truth(engine, "TRUE OR CAST(NULL AS BOOLEAN)") == true    // one TRUE is enough — the rescue
        truth(engine, "FALSE AND CAST(NULL AS BOOLEAN)") == false // one FALSE is enough
        truth(engine, "FALSE OR CAST(NULL AS BOOLEAN)") == null   // nothing decided it
        truth(engine, "TRUE AND CAST(NULL AS BOOLEAN)") == null

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] WHERE keeps TRUE and throws away BOTH false and unknown"() {
        // The sentence the whole episode turns on, as arithmetic. A row whose test came back
        // UNKNOWN is discarded exactly as if the test had been FALSE — which is why the bug
        // is silent: there is no third bucket to look in.
        expect:
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE TRUE').n == 79
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE FALSE').n == 0
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE CAST(NULL AS BOOLEAN)''').n == 0

        where:
        engine << ENGINES
    }

    // --- 2. THE OPENING BUG: the backlog that comes back empty -----------------------------

    @Unroll
    def "[#engine] the backlog query with an equals sign returns 0, and IS NULL returns 27"() {
        given:
        def withEquals = sqlFor(engine).firstRow(script("backlog-equals-null"))
        def withIsNull = sqlFor(engine).firstRow(script("backlog-is-null"))

        expect: "ZERO — the number the video puts on screen, and no error with it"
        withEquals.Unshipped == 0

        and: "TWENTY-SEVEN — and both queries ran, which is what makes the first one dangerous"
        withIsNull.Unshipped == 27

        and: "27 really is a third of the orders, which is how episode 40's article words it"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the backlog itself: five orders, oldest first, the eldest from 2022"() {
        given:
        def rows = sqlFor(engine).rows(script("backlog-oldest-first"))

        expect: "the rows the article prints"
        rows*.OrderID == [8, 11, 14, 17, 20]
        rows*.CustomerID == ["ALFKI", "AROUT", "BONAP", "DUMON", "FRANK"]

        and: "THE POINT OF THE SLIDE: order 8 has been sitting there since December 2022"
        (rows[0].OrderDate as String).startsWith("2022-12-05")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the hands-on query: last lesson's JOIN, this lesson's IS NULL"() {
        given:
        def rows = sqlFor(engine).rows(script("unshipped-with-customer-names"))

        expect: "the same five orders, now carrying the names episode 40 taught them to fetch"
        rows*.OrderID == [8, 11, 14, 17, 20]
        rows*.CompanyName == ["Alfreds Futterkiste", "Around the Horn", "Bon app'",
                              "Du monde entier", "Frankenversand"]

        where:
        engine << ENGINES
    }

    // --- 3. THE DEEPER TRAP: two reports that do not add up --------------------------------

    @Unroll
    def "[#engine] eighteen shipped in 2024 and thirty-four before it — which is not seventy-nine"() {
        given:
        int inYear = sqlFor(engine).firstRow(script("shipped-in-2024")).Orders
        int before = sqlFor(engine).firstRow(script("shipped-before-2024")).Orders

        expect: "the two numbers the manager asks for, both correct on their own"
        inYear == 18
        before == 34

        and: "THE BUG, AS ARITHMETIC: they are supposed to be every order, and they are not"
        inYear + before == 52
        inYear + before != 79

        and: "and the 27 missing from both are exactly the orders that never shipped"
        79 - (inYear + before) == 27
        sqlFor(engine).firstRow(script("backlog-is-null")).Unshipped == 27

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] wrapping the condition in NOT changes nothing — still thirty-four"() {
        // The beat the whole middle act exists for. A learner's next instinct after seeing
        // the hole is to negate the test, and it does not help: NOT flips TRUE and FALSE and
        // leaves UNKNOWN exactly where it was, so the same 27 rows are discarded again.
        expect:
        sqlFor(engine).firstRow(script("shipped-not-2024")).Orders == 34

        and: "IDENTICAL to the plain less-than, which is the thing that surprises people"
        sqlFor(engine).firstRow(script("shipped-not-2024")).Orders ==
                sqlFor(engine).firstRow(script("shipped-before-2024")).Orders

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] saying out loud what happens to the empty cells makes the books balance"() {
        given:
        int fixed = sqlFor(engine).firstRow(script("shipped-before-or-never")).Orders
        int inYear = sqlFor(engine).firstRow(script("shipped-in-2024")).Orders

        expect: "SIXTY-ONE — the 34 that shipped before 2024, plus the 27 that never shipped"
        fixed == 61
        fixed == 34 + 27

        and: "AND NOW IT ADDS UP, which is the whole payoff of the act"
        inYear + fixed == 79

        where:
        engine << ENGINES
    }

    // --- 4. NULL spreads: one empty piece empties the whole expression ---------------------

    @Unroll
    def "[#engine] a concatenation with one empty piece comes back empty from end to end"() {
        // EPISODE 20 PROMISED THIS ONE BY NAME — "if any piece of a || is empty, the ENTIRE
        // result becomes NULL … Region is empty for most of them and would [bite]". The row
        // that proves it is Berlin: the city is right there, and the label is gone anyway.
        given:
        def rows = sqlFor(engine).rows(script("label-broken"))

        expect: "the four rows the article prints — every City present, every Label missing"
        rows*.City == ["Berlin", "México D.F.", "México D.F.", "London"]
        rows.every { it.Region == null }
        rows.every { it.Label == null }

        and: "THE SIZE OF IT: 21 of the 25 labels vanish, not 21 of the 25 region cells"
        sqlFor(engine).firstRow('''SELECT count("City" || ', ' || "Region") AS n
                                   FROM "Customers"''').n == 4
        sqlFor(engine).firstRow('SELECT count("City") AS n FROM "Customers"').n == 25

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] COALESCE puts the line back"() {
        expect:
        sqlFor(engine).rows(script("label-coalesce"))*.Label ==
                ["Berlin, no region", "México D.F., no region",
                 "México D.F., no region", "London, no region"]

        and: "and every one of the 25 labels survives now, which is the whole fix"
        sqlFor(engine).firstRow('''SELECT count("City" || ', ' || COALESCE("Region", 'no region'))
                                   AS n FROM "Customers"''').n == 25

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] COALESCE around the AGGREGATE fills the empty answer: Japan is 0, blank, 0"() {
        // THE LOOP BACK TO EPISODE 25, and the reason this assertion exists. 25 showed this
        // exact query and left it unresolved: "count says 0, sum says nothing at all — this
        // is how a dashboard tile ends up blank instead of showing zero." It taught the
        // COUNT beside it as the DIAGNOSIS. This episode owes the CURE, and the cure is
        // COALESCE around the aggregate rather than around the column.
        given:
        def row = sqlFor(engine).firstRow(script("japan-freight-filled"))

        expect: "no order has ever shipped to Japan, and the query still returns ONE row"
        (row.Orders as int) == 0

        and: "sum over an empty set is NULL — not zero. That blank IS the bug 25 described"
        row.Freight == null

        and: "and COALESCE around the SUM is what turns it into a number a tile can print"
        dec(row."Freight, filled") == dec(0)

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] and the placement matters: INSIDE the aggregate changes nothing"() {
        // The article claims sum(COALESCE(col,0)) is POINTLESS while COALESCE(sum(col),0) is
        // the fix. That is a falsifiable claim about two different queries, so it is asserted
        // rather than asserted-by-prose. Over rows that exist the two spellings agree,
        // because sum already skips empty cells; over NO rows they disagree, which is the
        // entire point.
        expect: "over real rows the two are identical — so the inner form buys nothing"
        dec(sqlFor(engine).firstRow('''SELECT sum("Freight") AS a,
                                              sum(COALESCE("Freight", 0)) AS b
                                       FROM "Orders"''').a) ==
        dec(sqlFor(engine).firstRow('''SELECT sum("Freight") AS a,
                                              sum(COALESCE("Freight", 0)) AS b
                                       FROM "Orders"''').b)

        and: "over NO rows the inner form is still NULL, and only the outer one answers"
        sqlFor(engine).firstRow('''SELECT sum(COALESCE("Freight", 0)) AS inner_form
                                   FROM "Orders" WHERE "ShipCountry" = 'Japan' ''').inner_form == null
        dec(sqlFor(engine).firstRow('''SELECT COALESCE(sum("Freight"), 0) AS outer_form
                                       FROM "Orders" WHERE "ShipCountry" = 'Japan' ''').outer_form) == dec(0)

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] NULLIF turns a value into a NULL — the two zero-stock lines"() {
        given:
        def rows = sqlFor(engine).rows(script("nullif-in-stock"))

        expect:
        rows*.ProductName == ["Gorgonzola Telino", "Thuringer Rostbratwurst",
                              "Scottish Longbreads"]

        and: "the two zeros became genuine empties; the six is untouched"
        rows[0].InStock == null
        rows[1].InStock == null
        (rows[2].InStock as int) == 6

        where:
        engine << ENGINES
    }

    def "an UNGUARDED division by zero stops PostgreSQL, and NULLIF is why you guard it"() {
        // NOT @Unroll'd across ENGINES, because THE ENGINES GENUINELY DISAGREE and that
        // disagreement is what the article says. The learner types into CloudBeaver, which is
        // PostgreSQL, so PostgreSQL's behaviour is the one the article describes as "your
        // query stops". Read off the containerised postgres:16.2.
        when:
        sqlFor("postgres").rows('''SELECT "UnitsOnOrder" / "UnitsInStock" AS r
                                   FROM "Products"''')

        then:
        def e = thrown(Exception)
        e.message.toLowerCase().contains("division by zero")
    }

    def "DuckDB does NOT stop on it, which is exactly why the article names both engines"() {
        // The other half of the same claim. DuckDB hands back infinity rather than raising —
        // no error, no NULL, just a number that is not a number. Asserted so the article's
        // "some engines quietly hand you something worse" cannot drift into a guess.
        expect:
        sqlFor("duckdb").rows('''SELECT "UnitsOnOrder" / "UnitsInStock" AS r
                                 FROM "Products"''').size() == 20
    }

    @Unroll
    def "[#engine] with NULLIF the division runs on both engines and the two zero rows are NULL"() {
        // WHAT IS PORTABLE IS THE FIX, NOT THE FIGURE. PostgreSQL does integer division on
        // two smallints and DuckDB does floating point, so the ratio for a stocked line is a
        // different NUMBER on the two engines — which is why no such number appears anywhere
        // in this lesson. The part that IS identical, and the part the lesson teaches, is
        // that dividing by NULL gives NULL instead of an error.
        given:
        def rows = sqlFor(engine).rows('''SELECT "ProductName",
                                                 "UnitsOnOrder" / NULLIF("UnitsInStock", 0) AS r
                                          FROM "Products"
                                          WHERE "UnitsInStock" = 0
                                          ORDER BY "ProductName"''')

        expect:
        rows*.ProductName == ["Gorgonzola Telino", "Thuringer Rostbratwurst"]
        rows.every { it.r == null }

        where:
        engine << ENGINES
    }

    // --- 5. The loop back to lesson 25: the counts that were wrong all along ---------------

    @Unroll
    def "[#engine] count(*) is 79 and count of the column is 52 — episode 40 promised this gap"() {
        given:
        def row = sqlFor(engine).firstRow(script("count-star-vs-column"))

        expect: "the two numbers, side by side in one query, exactly as the slide shows them"
        row.Orders == 79
        row.Shipped == 52

        and: "THE GAP IS THE BACKLOG. 40's article says the two 'can disagree by a third of"
        and: "your business', and here that is not a figure of speech."
        row.Orders - row.Shipped == 27
        row.Orders - row.Shipped == sqlFor(engine).firstRow(script("backlog-is-null")).Unshipped

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the average time to ship is 5.8 days, and it is computed over 52 orders"() {
        // THE WRONG-NUMBER-IN-A-REPORT BUG, and the reason this episode has to loop back to
        // lesson 25 rather than leave the aggregates where it found them. AVG divides by the
        // count of VALUES, so an average over a column with 27 empties is an average over the
        // other 52 — and here the 27 it excludes are precisely the orders that took longest,
        // because they have not arrived at all. The number is not wrong. The report is.
        given:
        def avg = sqlFor(engine).firstRow(script("avg-days-to-ship")).AvgDaysToShip

        expect: "5.8 — compared BY VALUE, because DuckDB answers with a double and PostgreSQL"
        and: "with a numeric, and 5.8 must equal 5.80"
        dec(avg) == dec("5.8")

        and: "IT IS SUM OVER 52, NOT SUM OVER 79 — the arithmetic the slide states out loud"
        def totals = sqlFor(engine).firstRow('''SELECT
                sum(CAST("ShippedDate" AS DATE) - CAST("OrderDate" AS DATE)) AS s,
                count(CAST("ShippedDate" AS DATE) - CAST("OrderDate" AS DATE)) AS c,
                count(*) AS n
            FROM "Orders"''')
        (totals.s as int) == 304
        (totals.c as int) == 52
        (totals.n as int) == 79
        dec(avg) == new BigDecimal("304").divide(new BigDecimal("52"), 1, java.math.RoundingMode.HALF_UP)

        and: "spread over every order it would be 3.8, and neither number is the honest answer"
        new BigDecimal("304").divide(new BigDecimal("79"), 1, java.math.RoundingMode.HALF_UP) ==
                new BigDecimal("3.8")

        where:
        engine << ENGINES
    }

    // --- 6. The article's extra section: the one place NULLs ARE treated as equal ----------

    @Unroll
    def "[#engine] DISTINCT keeps ONE null and count(DISTINCT) skips it entirely"() {
        // ARTICLE ONLY — the video does not cover this, deliberately. It is the exception to
        // everything else on the page (a NULL is not equal to a NULL, except here), so it
        // needs a paragraph rather than a slide, and it needs to be right.
        given:
        def rows = sqlFor(engine).rows(script("regions-distinct"))

        expect: "FIVE rows: the four real regions, and one row standing for all 21 empties"
        rows.size() == 5
        rows*.Region == ["Isle of Wight", "Lara", "OR", "Táchira", null]

        and: "but count(DISTINCT) says FOUR, because a count of a column never counts empties"
        sqlFor(engine).firstRow('SELECT count(DISTINCT "Region") AS n FROM "Customers"').n == 4

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] GROUP BY gathers all 21 empty regions into ONE group"() {
        given:
        def rows = sqlFor(engine).rows(script("regions-grouped"))

        expect: "five groups, and the biggest one by far is the group of missing values"
        rows.size() == 5
        rows[0].Region == null
        rows[0].Customers == 21
        rows.drop(1)*.Customers == [1, 1, 1, 1]

        and: "which totals the whole table — no customer is lost, unlike every WHERE above"
        rows.sum { it.Customers as int } == 25

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the article's 'read but rarely write' section is true on both engines"() {
        // ARTICLE ONLY. These three are named as reading skills rather than taught, which is
        // precisely why they need pinning: nothing else in the lesson exercises them, so a
        // claim about them could rot for a year without a slide going wrong. Every one was
        // run before it was written down.
        expect: "IS NOT TRUE catches the unknowns as well as the falses — 34 plus the 27"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE ("ShippedDate" >= DATE '2024-01-01') IS NOT TRUE''').n == 61

        and: "and it agrees exactly with the OR ... IS NULL form the lesson actually teaches"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE ("ShippedDate" >= DATE '2024-01-01') IS NOT TRUE''').n ==
                sqlFor(engine).firstRow(script("shipped-before-or-never")).Orders

        and: "IS NOT DISTINCT FROM treats two NULLs as equal, where = would answer UNKNOWN"
        truth(engine, "CAST(NULL AS INTEGER) IS NOT DISTINCT FROM CAST(NULL AS INTEGER)") == true

        and: "ARITHMETIC SPREADS THE SAME WAY A CONCATENATION DOES: Freight plus nothing is"
        and: "nothing, on every one of the 79 rows — not Freight, which is what people expect"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "Freight" + CAST(NULL AS DECIMAL) IS NULL''').n == 79
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders" WHERE "Freight" IS NULL').n == 0

        where:
        engine << ENGINES
    }

    // --- 7. What the KOANS stand on --------------------------------------------------------
    //
    // THE KOANS DO NOT REUSE THE LESSON'S QUERIES. The lesson works on ORDERS that never
    // shipped and CUSTOMERS with no region; the koans work on the SUPPLIER list (3 of 6 with
    // no region), the staff list (1 of 3 with no manager) and the shelf. That is the house
    // convention — pom.xml states it as "the koans are related practice, not a blanked copy
    // of the gate" — and it exists so a learner applies the idea somewhere new instead of
    // retyping a query they just watched.
    //
    // Which is exactly why the koans need their own assertions. Nothing in the sections above
    // touches "Suppliers"."Region", "Employees"."ReportsTo" or "Products"."UnitsInStock", so a
    // shift in that data would surface as a RED KOAN ON A STUDENT'S SCREEN with a green gate
    // behind it — the worst possible place to discover it.

    @Unroll
    def "[#engine] koan 1: IS NULL names the three suppliers with no region"() {
        expect:
        sqlFor(engine).rows('''SELECT s."CompanyName"
                               FROM "Suppliers" s
                               WHERE s."Region" IS NULL
                               ORDER BY s."CompanyName"''')*.CompanyName ==
                ["Exotic Liquids", "Pasta Buttini s.r.l.", "Tokyo Traders"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 2: the two numbers the student predicts are 0 and 3"() {
        expect:
        sqlFor(engine).rows('''SELECT s."CompanyName" FROM "Suppliers" s
                               WHERE s."Region" = NULL''').size() == 0
        sqlFor(engine).rows('''SELECT s."CompanyName" FROM "Suppliers" s
                               WHERE s."Region" IS NULL''').size() == 3

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 3: IS NOT NULL names the other three, and their regions"() {
        expect:
        sqlFor(engine).rows('''SELECT s."CompanyName", s."Region"
                               FROM "Suppliers" s
                               WHERE s."Region" IS NOT NULL
                               ORDER BY s."CompanyName"''')
                .collect { [it.CompanyName, it.Region] } ==
                [["Grandma Kellys Homestead", "MI"],
                 ["New Orleans Cajun Delights", "LA"],
                 ["Pavlova Ltd", "Victoria"]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 4: the OR rescue returns five, and the koan's stated numbers are true"() {
        expect: "the solved koan"
        sqlFor(engine).rows('''SELECT s."CompanyName"
                               FROM "Suppliers" s
                               WHERE s."Region" <> 'Victoria'
                                  OR s."Region" IS NULL
                               ORDER BY s."CompanyName"''')*.CompanyName ==
                ["Exotic Liquids", "Grandma Kellys Homestead", "New Orleans Cajun Delights",
                 "Pasta Buttini s.r.l.", "Tokyo Traders"]

        and: "THE COMMENT'S ARITHMETIC, WORD FOR WORD: two rows from the not-equals, one from"
        and: "the equals, and three is not six. A wrong number in a hint tells a student their"
        and: "correct query is wrong, so it is checked rather than asserted in prose."
        sqlFor(engine).rows('''SELECT s."CompanyName" FROM "Suppliers" s
                               WHERE s."Region" <> 'Victoria' ''').size() == 2
        sqlFor(engine).rows('''SELECT s."CompanyName" FROM "Suppliers" s
                               WHERE s."Region" = 'Victoria' ''').size() == 1

        and: "and AND really would be useless in that blank, which the comment also claims"
        sqlFor(engine).rows('''SELECT s."CompanyName" FROM "Suppliers" s
                               WHERE s."Region" <> 'Victoria'
                                 AND s."Region" IS NULL''').size() == 0

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 5: NOT gives 2, and NOT with the rescue gives 5"() {
        expect:
        sqlFor(engine).rows('''SELECT s."CompanyName" FROM "Suppliers" s
                               WHERE NOT (s."Region" = 'Victoria')''').size() == 2
        sqlFor(engine).rows('''SELECT s."CompanyName" FROM "Suppliers" s
                               WHERE NOT (s."Region" = 'Victoria')
                                  OR s."Region" IS NULL''').size() == 5

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 6: COALESCE fills the three empty regions and touches nothing else"() {
        expect:
        sqlFor(engine).rows('''SELECT s."CompanyName", COALESCE(s."Region", 'no region') AS r
                               FROM "Suppliers" s
                               ORDER BY s."CompanyName"''')
                .collect { [it.CompanyName, it.r] } ==
                [["Exotic Liquids", "no region"],
                 ["Grandma Kellys Homestead", "MI"],
                 ["New Orleans Cajun Delights", "LA"],
                 ["Pasta Buttini s.r.l.", "no region"],
                 ["Pavlova Ltd", "Victoria"],
                 ["Tokyo Traders", "no region"]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 7: the address line, and the three that vanish without the fix"() {
        expect: "the solved koan"
        sqlFor(engine).rows('''SELECT s."CompanyName",
                                      s."City" || ', ' || COALESCE(s."Region", 'no region') AS w
                               FROM "Suppliers" s
                               ORDER BY s."CompanyName"''')
                .collect { [it.CompanyName, it.w] } ==
                [["Exotic Liquids", "London, no region"],
                 ["Grandma Kellys Homestead", "Ann Arbor, MI"],
                 ["New Orleans Cajun Delights", "New Orleans, LA"],
                 ["Pasta Buttini s.r.l.", "Salerno, no region"],
                 ["Pavlova Ltd", "Melbourne, Victoria"],
                 ["Tokyo Traders", "Tokyo, no region"]]

        and: "THE COMMENT'S CLAIM: written without the fix, only three of the six survive"
        sqlFor(engine).firstRow('''SELECT count(s."City" || ', ' || s."Region") AS n
                                   FROM "Suppliers" s''').n == 3

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 8: three employees, two with a manager"() {
        given:
        def row = sqlFor(engine).firstRow('''SELECT count(*) AS "Employees",
                                                    count(e."ReportsTo") AS "WithAManager"
                                             FROM "Employees" e''')

        expect:
        row.Employees == 3
        row.WithAManager == 2

        and: "THE COMMENT'S CLAIM: the one who is missing is the boss, not an absentee"
        sqlFor(engine).firstRow('''SELECT "Title" AS t FROM "Employees"
                                   WHERE "ReportsTo" IS NULL''').t.toString()
                .contains("Vice President")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 9: NULLIF blanks the two zeros and leaves the six alone"() {
        expect:
        sqlFor(engine).rows('''SELECT p."ProductName", NULLIF(p."UnitsInStock", 0) AS "InStock"
                               FROM "Products" p
                               ORDER BY p."UnitsInStock", p."ProductName"
                               LIMIT 3''')
                .collect { [it.ProductName, it.InStock == null ? null : it.InStock as int] } ==
                [["Gorgonzola Telino", null],
                 ["Thuringer Rostbratwurst", null],
                 ["Scottish Longbreads", 6]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 10: the whole query — the suppliers outside Victoria"() {
        given: "the query the student writes from scratch: both of today's ideas, in one go"
        def rows = sqlFor(engine).rows('''SELECT s."CompanyName",
                                                 COALESCE(s."Region", 'no region') AS r
                                          FROM "Suppliers" s
                                          WHERE s."Region" <> 'Victoria'
                                             OR s."Region" IS NULL
                                          ORDER BY s."CompanyName"''')

        expect:
        rows.collect { [it.CompanyName, it.r] } ==
                [["Exotic Liquids", "no region"],
                 ["Grandma Kellys Homestead", "MI"],
                 ["New Orleans Cajun Delights", "LA"],
                 ["Pasta Buttini s.r.l.", "no region"],
                 ["Tokyo Traders", "no region"]]

        and: "THE TWO MISTAKES THE COMMENT NAMES, and each really does cost what it says:"
        and: "forget the OR and you get two rows instead of five"
        sqlFor(engine).rows('''SELECT s."CompanyName" FROM "Suppliers" s
                               WHERE s."Region" <> 'Victoria' ''').size() == 2

        and: "forget the COALESCE and three of the five cells come back empty"
        sqlFor(engine).rows('''SELECT s."Region" AS r FROM "Suppliers" s
                               WHERE s."Region" <> 'Victoria'
                                  OR s."Region" IS NULL''').count { it.r == null } == 3

        where:
        engine << ENGINES
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/45-null-and-three-valued-logic/scripts/${name}.sql").text
    }

    /** How many rows have nothing in that column. The NULL inventory is this lesson's data.
     *
     *  CONCATENATED, NOT INTERPOLATED — see the note on truth() below. A table or column name
     *  is SQL text, not data, so a GString here would send the driver `FROM ?`. */
    private int nulls(String engine, String table, String column) {
        sqlFor(engine).firstRow(
                'SELECT count(*) AS n FROM "' + table + '" WHERE "' + column + '" IS NULL').n as int
    }

    /**
     * The value of one boolean expression, as the database itself answers it: true, false, or
     * null for SQL's UNKNOWN.
     *
     * CONCATENATED, NOT INTERPOLATED, AND THAT IS NOT A STYLE CHOICE. Groovy's Sql treats a
     * GString's ${...} slots as BIND PARAMETERS, so a """SELECT (${expr})""" would send the
     * driver `SELECT (?)` with the whole expression arriving as a string value. An expression
     * is SQL text, not data, so it has to be concatenated into a plain String first. Episode
     * 40's spec hit the same trap with a join keyword and wrote it down.
     */
    private Boolean truth(String engine, String expr) {
        sqlFor(engine).firstRow('SELECT (' + expr + ') AS t').t as Boolean
    }

    /** Compare decimals BY VALUE. DuckDB hands back a double and PostgreSQL a numeric, with
     *  different scales, so 5.8 and 5.80 must be equal or the comparison teaches nothing. */
    private static BigDecimal dec(Object v) { new BigDecimal(v.toString()).stripTrailingZeros() }
}
