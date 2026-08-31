package datazeus.learnsql.series1._35

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 35 HAVING vs WHERE                   ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _35  (Windows)   ./zeus.sh koans learnsql series1 _35  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * Same ten ideas, in the same order, asked about a different part of the business.
 * The lesson counted ORDERS per country; here you work in the WAREHOUSE — products
 * per supplier, prices per category, what is left on the shelf. Copying a query
 * across from the lesson will not work, which is the point: you learn the idea by
 * applying it somewhere new, not by retyping an answer you just watched.
 *
 * TEN KOANS, EASIEST FIRST, IN THE ORDER THE LESSON BUILDS THEM:
 *   1    the word itself — filter the GROUPS, not the rows
 *   2    the condition names an aggregate, because it is about the whole pile
 *   3    WHERE and HAVING in one query, each doing the job the other cannot
 *   4    the condition can be a total the report never shows
 *   5    THE TRAP: filter the rows first and the answer inverts (write the fix)
 *   6    PREDICT what the wrong clause actually returned, and what the right one does
 *   7    WHERE runs first, so a whole group can leave without saying so
 *   8    a different aggregate, and the comparison that goes with it
 *   9    say the aggregate again — HAVING cannot see the name you gave it
 *   10   the whole query, written from scratch
 *
 * These run on DuckDB. Every one is written so it returns the SAME answer against the
 * PostgreSQL in CloudBeaver — and koan 9 is the one place the two engines would
 * disagree if you solved it the lazy way. That disagreement IS koan 9.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * The tables these koans use, in full, so you can write a query without leaving this
 * file. When a koan asks for "what we charge for it", it is asking you to FIND the
 * column name below — not to have memorised it.
 *
 *   "Products" — 20 rows, 10 columns. "UnitsInStock" is how many we have on the
 *   shelf right now; "Discontinued" is true for lines we no longer sell. Every
 *   product belongs to one supplier ("SupplierID", 1-6) and one category
 *   ("CategoryID", 1-8).
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitsInStock"    SMALLINT       "UnitsOnOrder"    SMALLINT
 *     "ReorderLevel"    SMALLINT       "Discontinued"    BOOLEAN
 *
 *   "Customers" — 25 rows, 11 columns. Only the two used below are listed; the rest
 *   are address fields.
 *     "CustomerID"      VARCHAR        "CompanyName"     VARCHAR
 *     "Country"         VARCHAR        "City"            VARCHAR
 *
 * ── ONE FACT WORTH KNOWING BEFORE YOU START ─────────────────────────────────
 *
 * The 20 products are NOT spread evenly. Two suppliers carry most of the catalogue
 * and two categories hold almost all the expensive stock — which is exactly why
 * "filter the rows" and "filter the groups" give different answers here, the same
 * way they did in the lesson.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class HavingVsWhereKoans extends KoanBase {

    // 1) The word itself. You already know GROUP BY: it makes one pile per distinct value
    //    and runs the aggregate inside each pile. Now keep only SOME of those piles.
    //    Which suppliers give us more than three different products?
    //    The blank is a KEYWORD, and it is not WHERE — WHERE runs before any pile exists,
    //    so there would be no count to compare against yet.
    //    (Predict first: six suppliers stock 20 products between them, so most of them
    //    will NOT clear a bar of three.)
    def "filter the groups, not the rows"() {
        expect:
        shouldReturn([[4, 5], [6, 4]], '''
            SELECT "SupplierID", count(*) FROM "Products"
            GROUP BY "SupplierID"
            ___ count(*) > 3
            ORDER BY "SupplierID"
        ''')
    }

    // 2) A HAVING condition is a question about the WHOLE pile, so it has to name an
    //    aggregate — a plain column would be a question about one row.
    //    Which categories are expensive all the way down, where even the CHEAPEST thing
    //    in them costs more than 20?
    //    Fill in the aggregate. Not max — "the dearest is over 20" would let a category
    //    through on one expensive line while everything else in it is cheap.
    //    (Predict first: two of the eight categories qualify.)
    def "name the aggregate the condition is about"() {
        expect:
        shouldReturn([[6, 97.0000], [7, 23.2500]], '''
            SELECT "CategoryID", min("UnitPrice") FROM "Products"
            GROUP BY "CategoryID"
            HAVING ___("UnitPrice") > 20
            ORDER BY "CategoryID"
        ''')
    }

    // 3) Both filters, one query, and neither can do the other's job.
    //    Of the products we STILL SELL, which suppliers give us three or more?
    //    "we still sell it" is true or false for one product on its own — so it belongs in
    //    the clause that runs before the piles are built. Fill that keyword in.
    //    (Predict first: this is a harder bar than koan 1 in one way and an easier one in
    //    another, so expect a different set of suppliers — four of them.)
    def "WHERE and HAVING in the same query, each doing its own job"() {
        expect:
        shouldReturn([[1, 3], [3, 3], [4, 4], [6, 4]], '''
            SELECT "SupplierID", count(*) FROM "Products"
            ___ "Discontinued" = false
            GROUP BY "SupplierID"
            HAVING count(*) >= 3
            ORDER BY "SupplierID"
        ''')
    }

    // 4) The thing you filter on does not have to be on the page. This report shows how
    //    MANY products a category has, while quietly keeping only the categories holding
    //    more than 100 units in the warehouse.
    //    Fill in the aggregate that adds a column up across the whole pile.
    //    (Predict first: two categories, and notice the answer has nothing to do with the
    //    number shown beside it — one of them holds the most stock on only two products.)
    def "the condition can be a total the report never shows"() {
        expect:
        shouldReturn([[2, 3], [8, 2]], '''
            SELECT "CategoryID", count(*) FROM "Products"
            GROUP BY "CategoryID"
            HAVING ___("UnitsInStock") > 100
            ORDER BY "CategoryID"
        ''')
    }

    // 5) THE TRAP, and it is the whole reason HAVING exists. Write the filter yourself.
    //
    //    THE QUESTION: which suppliers only sell us cheap things — nothing over 50, ever?
    //
    //    The obvious answer is WHERE "UnitPrice" <= 50, and it RUNS, and it gives you SIX
    //    suppliers — every supplier we have. It is also wrong, and wrong in the worst way:
    //    every row it returns shows a top price under 50, so the report agrees with itself.
    //    It threw the expensive products away BEFORE looking, so of course nothing dear
    //    was left to find. Supplier 5 sells something at 123.79 and it would still be on
    //    that list.
    //
    //    The real question is about the whole pile: is this supplier's DEAREST product
    //    still under 50? Write that as one line — the clause and the condition.
    //    (Predict first: four of the six suppliers survive it.)
    def "the trap: ask about the pile, not about the rows"() {
        expect:
        shouldReturn([[1, 19.0000], [2, 22.0000], [3, 30.0000], [6, 38.0000]], '''
            SELECT "SupplierID", max("UnitPrice") FROM "Products"
            GROUP BY "SupplierID"
            ___
            ORDER BY "SupplierID"
        ''')
    }

    // 6) PREDICT — the size of the mistake, in numbers.
    //    Both queries below are already written and both run. One filters the ROWS first,
    //    the other filters the GROUPS. Say how many suppliers each one returns BEFORE you
    //    run it, then put your two numbers in.
    //    (This is the same pair as koan 5. The gap between the two numbers is how many
    //    suppliers a wrongly-placed filter would have quietly let through.)
    def "predict: how far wrong the wrong clause is"() {
        given: "the row-filtered version — the expensive products are gone before we look"
        int filteredTheRows = rows('''
            SELECT "SupplierID", max("UnitPrice") FROM "Products"
            WHERE "UnitPrice" <= 50
            GROUP BY "SupplierID"
        ''').size()

        and: "the group-filtered version — every product counted, then whole piles dropped"
        int filteredTheGroups = rows('''
            SELECT "SupplierID", max("UnitPrice") FROM "Products"
            GROUP BY "SupplierID"
            HAVING max("UnitPrice") <= 50
        ''').size()

        expect:
        filteredTheRows == ___
        filteredTheGroups == ___
    }

    // 7) WHERE runs first, one row at a time, before a single pile exists — so a group can
    //    lose every one of its rows and then simply not appear. Not as a zero. At all.
    //    Count the products under 50 in each category. Fill in the keyword.
    //    (Predict first: there are eight categories. You will get SEVEN rows back, and the
    //    missing one is category 6 — both of its products cost more than 50, so there was
    //    nothing left to make a pile out of. A zero is a fact; a missing row is a silence.)
    def "WHERE runs first, so a whole group can disappear"() {
        expect:
        shouldReturn([[1, 3], [2, 3], [3, 2], [4, 3], [5, 3], [7, 2], [8, 2]], '''
            SELECT "CategoryID", count(*) FROM "Products"
            ___ "UnitPrice" < 50
            GROUP BY "CategoryID"
            ORDER BY "CategoryID"
        ''')
    }

    // 8) Any aggregate can carry a HAVING, and the comparison is ordinary SQL.
    //    Which suppliers have no bargains at all — where even their CHEAPEST product costs
    //    more than 10? Fill in the comparison operator.
    //    (Predict first: three suppliers. Supplier 1's cheapest is exactly 10.00, so the
    //    operator you choose decides whether they are in or out — and "more than 10" does
    //    not include 10.)
    def "the comparison in a HAVING is ordinary SQL"() {
        expect:
        shouldReturn([[3, 12.5000], [5, 17.4500], [6, 12.5000]], '''
            SELECT "SupplierID", min("UnitPrice") FROM "Products"
            GROUP BY "SupplierID"
            HAVING min("UnitPrice") ___ 10
            ORDER BY "SupplierID"
        ''')
    }

    // 9) THE PORTABILITY ONE, and it is the koan most likely to bite you at work.
    //    The count is named "ProductCount" in the SELECT — so filtering on that name in the
    //    HAVING looks obvious. DuckDB, which is what these koans run on, ALLOWS IT. The
    //    PostgreSQL in CloudBeaver DOES NOT: it answers
    //        SQL Error [42703]: column "ProductCount" does not exist
    //    and it is right to. HAVING runs BEFORE SELECT, so at that moment the name has not
    //    been invented yet — it does not exist for another whole step.
    //    So the blank is NOT "ProductCount", even though that would go green here. Write
    //    the aggregate out again in full, exactly as it appears in the SELECT, and the
    //    query works on both engines.
    //    (Predict first: five of the six suppliers give us more than two products.)
    def "say the aggregate again — HAVING cannot see your alias"() {
        expect:
        shouldReturn([[1, 3], [3, 3], [4, 5], [5, 3], [6, 4]], '''
            SELECT "SupplierID", count(*) AS "ProductCount" FROM "Products"
            GROUP BY "SupplierID"
            HAVING ___ > 2
            ORDER BY "SupplierID"
        ''')
    }

    // 10) THE WHOLE QUERY, from nothing. No scaffolding, no clue about the shape.
    //
    //     THE QUESTION: which categories are running low — where all the stock we hold in
    //     that category, added together, comes to less than 50 units?
    //
    //     Return the category and its total stock, one row per category, in CategoryID
    //     order. Everything you need is in "Products": "CategoryID" and "UnitsInStock".
    //     (Predict first: three categories, and one of them is the expensive category you
    //     have already met twice today.)
    def "write the whole query: which categories are running low"() {
        expect:
        shouldReturn([[3, 35], [4, 41], [6, 29]], '''
            ___
        ''')
    }
}
