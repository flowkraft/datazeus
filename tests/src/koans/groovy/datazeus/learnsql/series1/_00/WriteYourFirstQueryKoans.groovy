package datazeus.learnsql.series1._00

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Master SQL · Series 1 · 00 Write Your First Query             ║
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
 * Tip: every query here also runs in CloudBeaver against the real Northwind —
 * try it there first, then come back and fill in the blank.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class WriteYourFirstQueryKoans extends KoanBase {

    // 1) Counting rows. Which function turns a table into "how many rows"?
    //    Fill the blank so this counts every order in the table. (Predict: how many?)
    def "count every order in the table"() {
        expect:
        shouldReturn 79, '''
            SELECT ___(*) FROM "Orders"
        '''
    }

    // 2) Filtering with WHERE — the half-open date range. We keep orders ON/AFTER
    //    June 1st; fill the operator that also stops them BEFORE July 1st (so all of
    //    June is captured, with no end-of-month surprise). Predict: more or less than 10?
    def "keep only the orders shipped in June 2024"() {
        expect:
        shouldReturn 4, '''
            SELECT count(*) FROM "Orders"
            WHERE "OrderDate" >= DATE '2024-06-01'
              AND "OrderDate" ___  DATE '2024-07-01'
        '''
    }

    // 3) Capstone — no scaffolding. Write the WHOLE query yourself, the one Leo typed
    //    in the video: "How many orders did we ship in June 2024?" (you should get 4).
    def "write your first query from scratch: orders shipped in June 2024"() {
        expect:
        shouldReturn 4, '''
            ___
        '''
    }
}
