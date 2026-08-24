package datazeus.learnsql.series1._10

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 10 WHERE, AND/OR/NOT, IN, BETWEEN     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _10  (Windows)   ./zeus.sh koans learnsql series1 _10  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * Twelve koans, one per idea in the lesson and in the same order: keep rows with =,
 * flip it with <>, compare a number, narrow with AND, tame OR with parentheses, test
 * against a list with IN and NOT IN, keep a range with BETWEEN and then without its
 * ends, build the date habit, match a pattern with LIKE, then write a whole
 * filtered query yourself.
 *
 * Tip: every query here also runs in CloudBeaver against the real Northwind —
 * try it there first, then come back and fill in the blank.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class WhereFilteringKoans extends KoanBase {

    // 1) WHERE keeps only the rows that pass the test. Text values go in 'single quotes',
    //    and the comparison is EXACT — 'germany' would match nothing. Which country
    //    keeps eleven of the twenty-five customers?
    def "keep only the German customers"() {
        expect:
        shouldReturn 11, '''
            SELECT count(*) FROM "Customers"
            WHERE "Country" = ___
        '''
    }

    // 2) The opposite test: NOT equal. One operator keeps everyone else.
    //    (SQL spells it <> — and accepts != as the same thing.)
    def "keep everyone who is NOT in Germany"() {
        expect:
        shouldReturn 14, '''
            SELECT count(*) FROM "Customers"
            WHERE "Country" ___ 'Germany'
        '''
    }

    // 3) Numbers are not text: they compare WITHOUT quotes. Below which price are
    //    there exactly two products? (Predict which two before you run it.)
    def "products cheaper than ten"() {
        expect:
        shouldReturn([["Filo Mix", 7.0000], ["Guarana Fantastica", 4.5000]], '''
            SELECT "ProductName", "UnitPrice" FROM "Products"
            WHERE "UnitPrice" < ___
        ''')
    }

    // 4) Two conditions, BOTH must pass — each one narrows the result further.
    //    Category 1 is Beverages. One word joins the two tests.
    def "beverages AND under ten — one product survives"() {
        expect:
        shouldReturn([["Guarana Fantastica"]], '''
            SELECT "ProductName" FROM "Products"
            WHERE "CategoryID" = 1 ___ "UnitPrice" < 10
        ''')
    }

    // 5) The trap from the lesson. You want: (beverages OR condiments) AND under 15 —
    //    which is TWO rows. Without parentheses, AND grabs first and you get four,
    //    two of them over the cap. Write the WHERE clause that returns two.
    def "parenthesize the OR — beverages or condiments, under 15"() {
        expect:
        shouldReturn 2, '''
            SELECT count(*) FROM "Products"
            WHERE ___
        '''
    }

    // 6) One test against a whole list: IN. Name the three countries — Mexico,
    //    Venezuela and Argentina — that together keep five customers.
    //    (Each value in its own 'single quotes', separated by commas.)
    def "customers in any of three countries"() {
        expect:
        shouldReturn 5, '''
            SELECT count(*) FROM "Customers"
            WHERE "Country" IN (___)
        '''
    }

    // 7) And the flip side — everyone the list does NOT name. Two words this time.
    def "customers in none of those countries"() {
        expect:
        shouldReturn 20, '''
            SELECT count(*) FROM "Customers"
            WHERE "Country" ___ ('Mexico', 'Venezuela', 'Argentina')
        '''
    }

    // 8) A range in one word — and BOTH ends are included. Aniseed Syrup costs
    //    exactly 10.0000: does it make the cut? Predict, then run.
    def "prices from ten to twenty, ends included"() {
        expect:
        shouldReturn 9, '''
            SELECT count(*) FROM "Products"
            WHERE "UnitPrice" ___ 10 AND 20
        '''
    }

    // 9) The same range with the ends EXCLUDED — no BETWEEN this time, two strict
    //    comparisons. Aniseed Syrup at exactly 10.0000 drops out: nine become eight.
    def "the same range, ends excluded"() {
        expect:
        shouldReturn 8, '''
            SELECT count(*) FROM "Products"
            WHERE "UnitPrice" ___ 10 AND "UnitPrice" ___ 20
        '''
    }

    // 10) The date habit, straight from episode 00 — and now you can read every
    //     character of it: greater-or-equal the FIRST day, and strictly before the
    //     first day of the NEXT month. Which operator finishes the June query?
    def "orders placed in June 2024"() {
        expect:
        shouldReturn 4, '''
            SELECT count(*) FROM "Orders"
            WHERE "OrderDate" >= DATE '2024-06-01'
              AND "OrderDate" ___ DATE '2024-07-01'
        '''
    }

    // 11) LIKE matches text patterns; % stands for "anything from here". Write the
    //     pattern (in 'single quotes') that keeps products STARTING WITH Ch.
    def "products whose name starts with Ch"() {
        expect:
        shouldReturn([["Chai"], ["Chang"], ["Chef Antons Cajun Seasoning"]], '''
            SELECT "ProductName" FROM "Products"
            WHERE "ProductName" LIKE ___
        ''')
    }

    // 12) The whole query — no scaffolding. Write the WHOLE query: name and price of every
    //     product that costs more than 90. (Two luxury items, one of them a sausage.)
    def "write it yourself: the luxury shelf"() {
        expect:
        shouldReturn([["Mishi Kobe Niku", 97.0000],
                      ["Thuringer Rostbratwurst", 123.7900]], '''
            ___
        ''')
    }
}
