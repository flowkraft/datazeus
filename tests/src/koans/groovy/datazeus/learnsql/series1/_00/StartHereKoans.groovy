package datazeus.learnsql.series1._00

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Master SQL · Series 1 · 00 Start Here                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     koans.bat learnsql series1 _00   (Windows)     ./koans.sh learnsql series1 _00   (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a number.
 *
 * Five koans on what THIS lesson showed you: the shape of a query, the table you read
 * from, the half-open date range, real Northwind data, and finally the whole hero query
 * written from scratch.
 *
 * Tip: every query here also runs in CloudBeaver against the real Northwind —
 * try it there first, then come back and fill in the blank.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class StartHereKoans extends KoanBase {

    // 1) The shape of the query you just ran. Which function turns a table into
    //    "how many rows"? (Predict first: how many orders does Northwind hold?)
    def "count every order in the table"() {
        expect:
        shouldReturn 79, '''
            SELECT ___(*) FROM "Orders"
        '''
    }

    // 2) Same shape, a different table. FROM decides WHICH table you read — fill in the
    //    one holding the customers. Keep the double quotes; the lesson explains why.
    def "the FROM decides which table you count"() {
        expect:
        shouldReturn 25, '''
            SELECT count(*) FROM ___
        '''
    }

    // 3) Filtering to one month — the half-open date range. We keep orders ON/AFTER
    //    June 1st; fill the operator that also stops them BEFORE July 1st, so all of
    //    June is captured with no end-of-month surprise. Predict: more or less than 10?
    def "keep only the orders from June 2024"() {
        expect:
        shouldReturn 4, '''
            SELECT count(*) FROM "Orders"
            WHERE "OrderDate" >= DATE '2024-06-01'
              AND "OrderDate" ___  DATE '2024-07-01'
        '''
    }

    // 4) Real data, not toy data. Ask for two columns instead of a count and you get
    //    real companies in real countries. Fill in the column holding the country.
    def "real data: five customers and where they are"() {
        expect:
        shouldReturn([["Alfreds Futterkiste", "Germany"],
                      ["Blauer See Delikatessen", "Germany"],
                      ["Drachenblut Delikatessen", "Germany"],
                      ["Frankenversand", "Germany"],
                      ["Königlich Essen", "Germany"]], '''
            SELECT "CompanyName", ___ FROM "Customers" LIMIT 5
        ''')
    }

    // 5) Capstone — no scaffolding. Write the WHOLE query yourself, the one Leo typed
    //    in the video: "How many orders did we get in June 2024?" (you should get 4).
    def "write the hero query from scratch: orders in June 2024"() {
        expect:
        shouldReturn 4, '''
            ___
        '''
    }
}
