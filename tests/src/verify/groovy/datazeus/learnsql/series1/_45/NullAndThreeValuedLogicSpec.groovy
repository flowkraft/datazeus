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
 *   15. regions-grouped              — the census behind "21 of the 25": ONE group of empties
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
 * AND THEN THE KOANS, ALL FIFTEEN, in their own section at the bottom. They deliberately do NOT
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
    def "[#engine] the two rows the not-a-value slide draws, cell for cell"() {
        // THE SLIDE'S TABLE IS NOT backlog-oldest-first.sql, and that is worth stating because
        // the first version of this assertion assumed it was and went red. That script selects
        // three columns — "OrderID", "CustomerID", "OrderDate" — and mentions "ShippedDate"
        // only in its WHERE, so there is no shipped-date cell in its output to be empty.
        //
        // The slide needs a FOURTH column, because its whole argument is that two rows are
        // indistinguishable in the column that matters: Leo says "look at those two, both
        // shipped dates are empty — they look identical", and that sentence needs the empty
        // cells visible. So the slide draws the same two orders with the shipped date brought
        // into the SELECT, and this is that query.
        //
        // NO .sql FILE FOR IT, deliberately: the slide shows no code card, so there is nothing
        // for a learner to type and nothing for the article to include. What must be true is
        // only that the four cells on screen are the four cells the database returns.
        given:
        def rows = sqlFor(engine).rows('''SELECT "OrderID", "CustomerID", "OrderDate", "ShippedDate"
                                          FROM "Orders"
                                          WHERE "ShippedDate" IS NULL
                                          ORDER BY "OrderDate"
                                          LIMIT 2''')

        expect: "the two oldest unshipped orders, exactly as the slide prints them"
        rows*.OrderID == [8, 11]
        rows*.CustomerID == ["ALFKI", "AROUT"]
        (rows[0].OrderDate as String).startsWith("2022-12-05")
        (rows[1].OrderDate as String).startsWith("2022-12-26")

        and: "AND BOTH SHIPPED DATES ARE EMPTY, which is the only reason the slide exists —"
        and: "two rows that are identical in that column and still not equal to each other"
        rows[0].ShippedDate == null
        rows[1].ShippedDate == null

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

        and: "THE TWO CONDITIONS REALLY DO PARTITION EVERY DATE, which is what makes the"
        and: "slide's claim a fact about logic rather than about this month's data. The pair is"
        and: "`>= X` and `< X` on the SAME X, so no row that has a date can fall between them"
        and: "and none can fall outside — the only way out is to have no date at all."
        and: "THIS IS WHY THE SLIDE ASKS 'shipped in 2024 OR LATER' AND NOT 'shipped in 2024'."
        and: "It used to ask the second while running the first, and it was right only because"
        and: "nothing in this database shipped after 2024-06-13. Asking for a bounded year —"
        and: "EXTRACT(YEAR ...) = 2024 — would have moved that assumption rather than removed"
        and: "it, because 'in 2024' plus 'before 2024' covers everything only while no order"
        and: "ships later. The unbounded pair needs no such promise, and this proves it."
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders"
                                   WHERE "ShippedDate" IS NOT NULL
                                     AND NOT ( ("ShippedDate" >= DATE '2024-01-01')
                                            OR ("ShippedDate" <  DATE '2024-01-01') )''').n == 0

        and: "so the two reports together are exactly the orders that HAVE a shipped date"
        inYear + before == sqlFor(engine).firstRow(
                'SELECT count("ShippedDate") AS n FROM "Orders"').n

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
    def "[#engine] GROUP BY on the shipped year makes FOUR piles, not three: 2, 32, 18 and 27 empty"() {
        // The video's ask-the-column slide. Leo predicts three piles (2022, 2023, 2024); the
        // empty dates come back as a fourth, and its 27 is the backlog from the opening. The
        // SQL is the slide's own, character for character.
        given:
        def rows = sqlFor(engine).rows('''SELECT EXTRACT(YEAR FROM "ShippedDate") AS "ShippedYear",
                                                count(*) AS "Orders"
                                         FROM "Orders"
                                         GROUP BY EXTRACT(YEAR FROM "ShippedDate")
                                         ORDER BY "ShippedYear" NULLS LAST''')

        expect: "four rows, the empty pile last"
        rows.size() == 4
        rows[0..2].collect { it.ShippedYear as int } == [2022, 2023, 2024]
        rows[3].ShippedYear == null

        and: "the counts the result card prints"
        rows.collect { it.Orders as int } == [2, 32, 18, 27]

        and: "2 + 32 is the 34 before 2024, and the piles add back up to all 79"
        (rows[0].Orders as int) + (rows[1].Orders as int) == 34
        rows.sum { it.Orders as int } == 79

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

        expect: "the three rows the article and the slide print — City present, Label missing"
        rows*.City == ["Berlin", "México D.F.", "México D.F."]
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
                ["Berlin, no region", "México D.F., no region", "México D.F., no region"]

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
        // NOT ARTICLE-ONLY ANY MORE. The video prints this result whole on `label-question`,
        // as the stake before the mailing labels break — so the ROW ORDER is on screen too,
        // and it is pinned below rather than left to the tie-break. The ordering is
        // deterministic on both engines by construction: the empty group is alone on 21 so
        // "Customers" DESC settles it first, and the four singletons have distinct names.
        given:
        def rows = sqlFor(engine).rows(script("regions-grouped"))

        expect: "five groups, and the biggest one by far is the group of missing values"
        rows.size() == 5
        rows[0].Region == null
        rows[0].Customers == 21
        rows.drop(1)*.Customers == [1, 1, 1, 1]

        and: "in the order the video's card prints them, top to bottom"
        rows*.Region == [null, "Isle of Wight", "Lara", "OR", "Táchira"]

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
    // no region), the staff list (1 of 3 with no manager) and the shelf. FIFTEEN of them,
    // grouped by the four places the lesson ends on. That is the house
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
    def "[#engine] koan 4: on the supplier list = '' finds 0, IS NULL finds 3, and <> '' finds 3"() {
        expect: "not one supplier holds an empty string, so the query people write finds none"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Suppliers"
                                   WHERE "Region" = '' ''').n == 0

        and: "while the test that answers finds all three"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Suppliers"
                                   WHERE "Region" IS NULL''').n == 3

        and: "AND THE THIRD PREDICTION, which is the one that catches people: 3, not 6"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Suppliers"
                                   WHERE "Region" <> '' ''').n == 3

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 5: the OR rescue returns five, and the koan's stated numbers are true"() {
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
    def "[#engine] koan 6: NOT gives 2, and NOT with the rescue gives 5"() {
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
    def "[#engine] koan 7: the self-join on ReportsTo returns two of the three employees"() {
        // THE NULLABLE FOREIGN KEY, which is the shape a practitioner actually meets. The boss
        // reports to nobody, so his "ReportsTo" is empty, so his ON is UNKNOWN, so he is gone.
        expect: "the plain join loses the one whose manager column is empty"
        sqlFor(engine).rows('''SELECT e."FirstName" AS e, m."FirstName" AS m
                               FROM "Employees" e
                               JOIN "Employees" m ON e."ReportsTo" = m."EmployeeID"
                               ORDER BY e."FirstName"''').collect { [it.e, it.m] } ==
                [["Janet", "Andrew"], ["Nancy", "Andrew"]]

        and: "there were three employees going in, and a LEFT JOIN is what keeps all three"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Employees"').n == 3
        sqlFor(engine).rows('''SELECT e."FirstName" AS e
                               FROM "Employees" e
                               LEFT JOIN "Employees" m ON e."ReportsTo" = m."EmployeeID"''').size() == 3

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 8: NULLS LAST pins the sort, and it is the same on both engines"() {
        // THE KOAN TEACHES THE EXPLICIT FORM ON PURPOSE. A bare ORDER BY ... DESC puts the
        // blanks last on DuckDB and first on PostgreSQL, so a koan asserting the default would
        // go green for the student and be wrong in CloudBeaver. This asserts on BOTH engines,
        // which is the proof that the koan's answer is portable.
        expect:
        sqlFor(engine).rows('''SELECT "CompanyName" AS c, "Region" AS r
                               FROM "Suppliers"
                               ORDER BY "Region" DESC NULLS LAST, "CompanyName"''')
                .collect { [it.c, it.r] } ==
                [["Pavlova Ltd", "Victoria"],
                 ["Grandma Kellys Homestead", "MI"],
                 ["New Orleans Cajun Delights", "LA"],
                 ["Exotic Liquids", null],
                 ["Pasta Buttini s.r.l.", null],
                 ["Tokyo Traders", null]]

        and: "and nothing was lost on the way — all six are still there"
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Suppliers"').n == 6

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 9: COALESCE fills the three empty regions and touches nothing else"() {
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
    def "[#engine] koan 10: the address line, and the three that vanish without the fix"() {
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
    def "[#engine] koan 11: three employees, two with a manager"() {
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
    def "[#engine] koan 12: NULLIF gives the division nothing to divide by, instead of a zero"() {
        given: "how far ahead each line is sold — units on order over units on the shelf"
        // WHY THE KOAN DIVIDES RATHER THAN JUST PRINTING NULLIF's OUTPUT. Printing it shows
        // WHAT the function returns; dividing shows WHY anyone would want it. Gorgonzola
        // Telino has 70 on order and 0 in stock, and "70 divided by nothing" has no answer —
        // so the honest result is an empty cell, which is exactly what NULLIF arranges.
        //
        // `* 1.0` IS LOAD-BEARING AND MUST NOT BE TIDIED AWAY. Both columns are SMALLINT, and
        // PostgreSQL's `/` on two integers is INTEGER division — 10/6 would come back 1, not
        // 1.67, and the koan would teach a wrong number on one engine and the right one on the
        // other. Multiplying by 1.0 makes it numeric on PostgreSQL and double on DuckDB, and
        // round(...,2) then agrees. This is also why the comparison below is BY VALUE.
        def rows = sqlFor(engine).rows('''SELECT p."ProductName" AS n,
                                                 p."UnitsOnOrder" AS o,
                                                 p."UnitsInStock" AS s,
                                                 round(p."UnitsOnOrder" * 1.0
                                                       / NULLIF(p."UnitsInStock", 0), 2) AS t
                                          FROM "Products" p
                                          WHERE p."UnitsOnOrder" > 0
                                          ORDER BY p."ProductName"''')

        expect: "six lines are on order"
        rows.size() == 6

        and: "the one with nothing on the shelf comes back with NO answer, and is still a row"
        def g = rows.find { it.n == "Gorgonzola Telino" }
        g.o as int == 70
        g.s as int == 0
        g.t == null

        and: "EMPTY IS NOT ZERO: a zero here would claim the line is not sold ahead at all"
        g.t != 0

        and: "every other line divides normally"
        rows.findAll { it.t != null }.collect { [it.n, dec(it.t).setScale(2, java.math.RoundingMode.HALF_UP)] } ==
                [["Aniseed Syrup", dec("5.38")],
                 ["Chang", dec("2.35")],
                 ["Gnocchi di nonna Alice", dec("0.48")],
                 ["Queso Cabrales", dec("1.36")],
                 ["Scottish Longbreads", dec("1.67")]]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 13: Germany averages over 22 of its 32 orders"() {
        given: "the koan's own query, in the portable date form both engines share"
        def row = sqlFor(engine).firstRow('''SELECT o."ShipCountry",
                                                    count(*)                AS "Orders",
                                                    count(o."ShippedDate")  AS "Shipped",
                                                    avg(CAST(o."ShippedDate" AS DATE)
                                                      - CAST(o."OrderDate"   AS DATE)) AS "AvgDays"
                                             FROM "Orders" o
                                             WHERE o."ShipCountry" = 'Germany'
                                             GROUP BY o."ShipCountry"''')

        expect: "the two numbers the koan asks the student to predict"
        row.Orders == 32
        row.Shipped == 22

        and: "5.95 BY VALUE — DuckDB answers with a double, PostgreSQL with a numeric"
        dec(row.AvgDays).setScale(2, java.math.RoundingMode.HALF_UP) == dec("5.95")

        and: "THE POINT OF THE KOAN: the denominator is the VALUES, never the rows"
        dec(row.AvgDays).setScale(2, java.math.RoundingMode.HALF_UP) !=
                dec(row.Orders).setScale(2, java.math.RoundingMode.HALF_UP)

        and: "ten German orders have no shipped date, and they are absent, not zero"
        row.Orders - row.Shipped == 10

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 14: the missing postcodes are not spread out, they are two whole markets"() {
        given: "the two-second habit, asked of the column a mailing would join on"
        // THIS USED TO BE `GROUP BY "Customers"."Region"`, WHICH WAS THE ARTICLE'S OWN QUERY
        // (scripts/regions-grouped.sql) down to the alias — a koan you could answer by
        // scrolling up the lesson page, which is precisely what koans are not for. The idea
        // is unchanged: interrogate a column BEFORE you filter or join on it. What changed is
        // that it now asks a question the lesson never asks, of a column the lesson never
        // touches — and the answer is more useful than a total, because it says WHICH rows go.
        def rows = sqlFor(engine).rows('''SELECT s."Country" AS c,
                                                 count(*) AS n,
                                                 count(*) - count(s."PostalCode") AS missing
                                          FROM "Suppliers" s
                                          GROUP BY s."Country"
                                          ORDER BY 3 DESC, 1''')

        expect: "five countries supply us"
        rows.collect { [it.c, it.n as int, it.missing as int] } ==
                [["USA", 2, 2], ["UK", 1, 1], ["Australia", 1, 0], ["Italy", 1, 0], ["Japan", 1, 0]]

        and: "THE POINT: the three gaps are not one-per-country, they are ALL of two countries"
        rows.findAll { (it.missing as int) > 0 }.every { (it.missing as int) == (it.n as int) }

        and: "so a join on the postcode keeps four suppliers and silently drops three"
        rows.sum { it.missing as int } == 3
        rows.sum { it.n as int } == 6

        and: "the count(*) - count(column) arithmetic is the same three the direct test finds"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Suppliers"
                                   WHERE "PostalCode" IS NULL''').n == 3

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 15: the whole query — the suppliers outside Victoria"() {
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

    // --- 8. THE THREE THINGS PEOPLE ACTUALLY HIT AT WORK ------------------------------------
    // Added 2026-09-09 with three new slides. Each one is a bug a practitioner meets in a real
    // project rather than a corner case: looking for blanks with = '', joining on a column that
    // has blanks in it, and sorting a column that has blanks in it.

    @Unroll
    def "[#engine] an empty string is NOT a null, and looking for blanks with = '' finds none"() {
        // THE BUG: somebody wants the customers with no region and writes = ''. It runs, it
        // returns nothing, and it looks like there are no blanks — the same silent shape as the
        // episode's opening `= NULL`, but for a completely different reason. `= NULL` is UNKNOWN;
        // `= ''` is a perfectly ordinary comparison that is FALSE, because a null is not an
        // empty string. Both come back empty and neither complains.
        expect: "the dataset has 21 blank regions and NOT ONE empty string"
        nulls(engine, "Customers", "Region") == 21
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Customers" WHERE "Region" = '' ''').n == 0

        and: "so the query a person actually writes finds none of them"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Customers" WHERE "Region" IS NULL''').n == 21

        and: "AND ITS OPPOSITE IS WORSE — <> '' drops the blanks too, silently: 4, not 25"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Customers" WHERE "Region" <> '' ''').n == 4

        and: "the two are different things to the database, and it will say so"
        truth(engine, "'' IS NULL") == false
        truth(engine, "CAST(NULL AS VARCHAR) = ''") == null   // UNKNOWN, not false

        and: "AND THE ARTICLE'S OWN SCRIPT, RUN AS WRITTEN, agrees — see the note on script()"
        // Read off disk rather than re-typed. The assertions above prove the CLAIM; this one
        // proves the FILE the reader copies still makes it. Its column is aliased `= ''`,
        // which is not a legal accessor, so take it positionally.
        sqlFor(engine).firstRow(script("region-empty-string")).getAt(0) == 0

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] a join ON a column with blanks in it matches NOTHING, not even blank to blank"() {
        // THE BUG, AND WHY IT IS THIS PAIR OF COLUMNS. The bundled Northwind has NO nullable
        // foreign key that is actually null — EmployeeID, ShipVia and CustomerID are 79/79
        // populated — so there is no honest way to show a broken FK join on this data. What the
        // data DOES have is two region columns that are mostly empty, and "show me each customer
        // beside the orders shipped in their own region" is a real question somebody asks.
        // It comes back with nothing at all, and no warning.
        given:
        def customerRegionNulls = nulls(engine, "Customers", "Region")
        def shipRegionNulls = nulls(engine, "Orders", "ShipRegion")

        expect: "21 of 25 customers and ALL 79 orders have no region"
        customerRegionNulls == 21
        shipRegionNulls == 79
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79

        and: "so the join returns zero rows — the blanks do not find each other"
        sqlFor(engine).firstRow('''SELECT count(*) AS n
                                   FROM "Customers" c
                                   JOIN "Orders" o ON c."Region" = o."ShipRegion"''').n == 0

        // IS NOT DISTINCT FROM is the operator that DOES treat blank as equal to blank, so the
        // count it returns is what the plain join would have found if nulls matched. That it is
        // 21 x 79 rather than 0 is the proof that the join is DROPPING them, not finding none.
        and: "and if blank did match blank it would be 21 x 79"
        sqlFor(engine).firstRow('''SELECT count(*) AS n
                                   FROM "Customers" c
                                   JOIN "Orders" o
                                     ON c."Region" IS NOT DISTINCT FROM o."ShipRegion"''').n ==
                customerRegionNulls * shipRegionNulls

        and: "AND THE ARTICLE'S OWN SCRIPT, RUN AS WRITTEN, returns the same nothing"
        sqlFor(engine).firstRow(script("join-on-blank-regions")).Rows == 0

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] ORDER BY does not lose the blanks, and where it puts them is NOT portable"() {
        // THE BUG: a report sorted by a nullable column looks fine on the machine it was written
        // on and puts 27 rows somewhere else in production. Unlike everything else in this
        // lesson nothing is LOST here — which is exactly why it is missed.
        given:
        def asc = sqlFor(engine).rows('''SELECT "ShippedDate" AS d FROM "Orders" ORDER BY "ShippedDate"''')
        def desc = sqlFor(engine).rows('''SELECT "ShippedDate" AS d FROM "Orders" ORDER BY "ShippedDate" DESC''')

        expect: "every row survives the sort — all 79, blanks included"
        asc.size() == 79
        desc.size() == 79
        asc.count { it.d == null } == 27

        and: "ASC agrees on both engines: the 27 blanks go LAST"
        asc.take(52).every { it.d != null }
        asc.drop(52).every { it.d == null }

        // DESC IS WHERE THEY PART, AND THIS IS THE WHOLE POINT OF THE SLIDE. Measured
        // 2026-09-09 on both engines, not taken from either manual:
        //     DuckDB      ORDER BY … DESC  ->  the 27 blanks come LAST
        //     PostgreSQL  ORDER BY … DESC  ->  the 27 blanks come FIRST
        // The SQL standard leaves it implementation-defined, so both are correct and a report
        // written against one silently reorders on the other. This course has the learner on
        // BOTH — the koans run on DuckDB, CloudBeaver talks to PostgreSQL — so it is not a
        // theoretical portability worry, it is something they can see today.
        // IF THIS ASSERTION EVER FAILS, an engine changed its default: fix the SLIDE, not this.
        and: "DESC: DuckDB puts the blanks last, PostgreSQL puts them first"
        if (engine == "duckdb") {
            assert desc.take(52).every { it.d != null }
            assert desc.drop(52).every { it.d == null }
        } else {
            assert desc.take(27).every { it.d == null }
            assert desc.drop(27).every { it.d != null }
        }

        and: "SAYING IT EXPLICITLY IS PORTABLE, and that is the lesson"
        def explicitLast = sqlFor(engine).rows(
                '''SELECT "ShippedDate" AS d FROM "Orders" ORDER BY "ShippedDate" DESC NULLS LAST''')
        explicitLast.take(52).every { it.d != null }
        explicitLast.drop(52).every { it.d == null }

        and: "AND THE ARTICLE'S OWN SCRIPT, RUN AS WRITTEN, sorts the same way on both engines"
        // The script carries the explicit NULLS LAST, which is precisely why it is portable
        // and why it is safe to assert one ordering here for both engines.
        def fromScript = sqlFor(engine).rows(script("shipped-date-sorted"))
        fromScript.size() == 79
        fromScript.take(52).every { it.ShippedDate != null }
        fromScript.drop(52).every { it.ShippedDate == null }

        where:
        engine << ENGINES
    }

    // --- 9. THE KOAN FILE ITSELF, RUN AS WRITTEN --------------------------------------------
    //
    // WHY THIS EXISTS, AND THE DAY IT WOULD HAVE PAID FOR ITSELF. Every `koan N:` feature above
    // asserts the ANSWER a koan should produce — but each of them RE-TYPES the koan's query,
    // with its own aliases. So the spec proved the answer was right while never checking that
    // the koan file still asked that question. On 2026-09-11 two koans were found to be the
    // lesson's own queries with one word blanked (koan 12 was scripts/nullif-in-stock.sql, koan
    // 14 was scripts/regions-grouped.sql), were rewritten, and NOTHING IN THIS SPEC NOTICED —
    // it went green against queries that no longer existed in the koans file.
    //
    // WHAT THIS DOES INSTEAD. It reads NullAndThreeValuedLogicKoans.groovy off disk, pulls out
    // each koan's own SQL and its own declared expected rows, substitutes the intended answer
    // for the `___`, runs it, and compares. The koan file becomes the source of truth the way
    // scripts/*.sql already is via script(), so a koan whose query changes must either still
    // return what it claims or fail here, loudly, on BOTH engines.
    //
    // THE ANSWER TABLE IS THE ONLY THING WRITTEN DOWN TWICE, and it has to be: the blank is by
    // definition not in the file. Keep it in koan order; a missing entry fails the feature
    // rather than silently skipping, which is the point.

    @Unroll
    def "[#engine] koan file, as written: '#title' runs and returns exactly what it claims"() {
        given: "the koan's own text, with the intended answer where the learner's blank is"
        def sql = koanSql(title)

        expect:
        rowsOf(engine, sql) == koanExpected(title)

        where:
        [engine, title] << [ENGINES, ANSWERS.keySet() as List].combinations()
    }

    @Unroll
    def "[#engine] koan file, as written: the three PREDICT koans' queries still run"() {
        // These three blank a predicted NUMBER rather than a piece of SQL, so their queries are
        // already complete in the file. Nothing to substitute — but they still have to run, and
        // the counts they ask the learner to predict are asserted in their own features above.
        expect:
        PREDICT_KOANS.every { t -> koanQueries(t).every { q -> rowsOf(engine, q) != null } }

        and: "and there really are three of them, so a fourth cannot appear unnoticed"
        PREDICT_KOANS.size() == 3

        where:
        engine << ENGINES
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    /** The koans file, read the same way script() reads the lesson's SQL. */
    private static String koansSource() {
        new File("src/koans/groovy/datazeus/learnsql/series1/_45/NullAndThreeValuedLogicKoans.groovy").text
    }

    /** One koan's source, from `def "title"()` to the end of its body. */
    private static String koanBody(String title) {
        def src = koansSource()
        int at = src.indexOf('def "' + title + '"()')
        assert at >= 0: "no koan titled '${title}' in the koans file — it was renamed or removed"
        int next = src.indexOf('\n    def "', at + 1)
        next < 0 ? src.substring(at) : src.substring(at, next)
    }

    /** Every ''' … ''' SQL literal inside one koan. */
    private static List<String> koanQueries(String title) {
        (koanBody(title) =~ /(?s)'''(.*?)'''/).collect { it[1] }
    }

    /**
     * THE INTENDED ANSWER FOR EACH BLANK, in the order the blanks appear in that koan's SQL.
     * A koan with two blanks needs two entries (koan 13 asks for the same column twice).
     */
    private static final Map<String, List<String>> ANSWERS = [
            "the test for an empty cell is two words, not an equals sign": ["IS NULL"],
            "the other half of the pair: the rows that do have a value"  : ["IS NOT NULL"],
            "the trap: a not-equals filter leaves the empty rows out"    : ["OR"],
            "an ON is a comparison too, so a join drops the empty ones"  : ['"EmployeeID"'],
            "a sort keeps every row, but you must say where the empties go": ["NULLS LAST"],
            "give the empty cell something to stand in for it"           : ["COALESCE"],
            // The koan says "write the whole middle of that expression" — so the answer is the
            // FIXED expression, not the bare column. A bare s."Region" is what the learner is
            // being shown to avoid, and it empties three of the six lines end to end.
            "one missing piece empties the whole line"                   : ['''COALESCE(s."Region", 'no region')'''],
            "count(*) counts rows, count(column) counts values"          : ['"ReportsTo"'],
            "NULLIF is COALESCE backwards"                               : ["NULLIF"],
            "an average divides by the values it found, not the rows"    : ['"ShippedDate"', '"ShippedDate"'],
            "ask the column before you filter it"                        : ['"PostalCode"'],
            "write the whole query: the suppliers outside Victoria"      : ['''
                SELECT s."CompanyName", COALESCE(s."Region", 'no region')
                FROM "Suppliers" s
                WHERE s."Region" <> 'Victoria' OR s."Region" IS NULL
                ORDER BY s."CompanyName"
            '''],
    ]

    /** The koans whose blank is a predicted value rather than a piece of SQL. */
    private static final List<String> PREDICT_KOANS = [
            "predict: what an equals sign against NULL really returns",
            "predict: an empty string is a value, and a missing one is not",
            "predict: NOT does not rescue you either",
    ]

    /** One koan's SQL with its blanks filled in, ready to run. */
    private static String koanSql(String title) {
        def qs = koanQueries(title)
        assert qs.size() == 1: "koan '${title}' has ${qs.size()} queries; this helper expects one"
        def sql = qs[0]
        def answers = ANSWERS[title]
        int found = sql.count("___")
        assert found == answers.size():
                "koan '${title}' has ${found} blank(s) but the answer table has ${answers.size()}"
        answers.each { a -> sql = sql.replaceFirst(/___/, java.util.regex.Matcher.quoteReplacement(a)) }
        sql
    }

    /** The rows the koan file itself declares, parsed out of its own shouldReturn(...) call. */
    private static List koanExpected(String title) {
        def m = (koanBody(title) =~ /(?s)shouldReturn\(\s*(\[.*?\])\s*,\s*'''/)
        assert m.find(): "koan '${title}' does not declare its expected rows with shouldReturn"
        // Through the SAME normaliser as the live rows, or 5.38 would not equal 5.3800.
        (Eval.me(m.group(1)) as List).collect { row -> (row as List).collect { plain(it) } }
    }

    /** Run a query and reduce it to plain positional values, comparable across both engines. */
    private List rowsOf(String engine, String sql) {
        sqlFor(engine).rows(sql).collect { r -> (0..<r.size()).collect { i -> plain(r.getAt(i)) } }
    }

    /**
     * ONE SHAPE FOR BOTH ENGINES. DuckDB and PostgreSQL disagree about the Java type behind the
     * same value — Integer vs Long vs BigDecimal, and different scales for the same decimal — so
     * every number becomes a scale-stripped BigDecimal before anything is compared. Without this
     * the feature would fail on 5.38 vs 5.3800 and teach nothing.
     */
    private static Object plain(Object v) {
        v == null ? null : (v instanceof Number ? dec(v) : v)
    }

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
