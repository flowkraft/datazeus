package datazeus.learnsql.series1._05

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 05 Meet Your Data With SELECT         ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _05  (Windows)   ./zeus.sh koans learnsql series1 _05  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * Eight koans, one per idea in the lesson and in the same order: ask for every column,
 * name one, name two, choose their order, limit the rows, change how a number is
 * displayed, ask the catalog for a type, then write a whole query yourself.
 *
 * Tip: every query here also runs in CloudBeaver against the real Northwind —
 * try it there first, then come back and fill in the blank.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class MeetYourDataWithSelectKoans extends KoanBase {

    // 1) Ask a small table for EVERYTHING. One character does it.
    def "ask for every column: the whole Shippers table"() {
        expect:
        shouldReturn([[1, "Speedy Express", "(503) 555-9831"],
                      [2, "United Package", "(503) 555-3199"],
                      [3, "Federal Shipping", "(503) 555-9931"]], '''
            SELECT ___ FROM "Shippers"
        ''')
    }

    // 2) Now the opposite: the select list decides WHICH columns come back. Ask for the
    //    one column that holds a category's name, and you get it for every row.
    def "name one column: every category name"() {
        expect:
        shouldReturn([["Beverages"], ["Condiments"], ["Confections"], ["Dairy Products"],
                      ["Grains/Cereals"], ["Meat/Poultry"], ["Produce"], ["Seafood"]], '''
            SELECT ___ FROM "Categories"
        ''')
    }

    // 3) Two columns, separated by a comma. Which one holds the surname?
    def "name two columns: first name and surname"() {
        expect:
        shouldReturn([["Nancy", "Davolio"], ["Andrew", "Fuller"], ["Janet", "Leverling"]], '''
            SELECT "FirstName", ___ FROM "Employees"
        ''')
    }

    // 4) The table stores LastName BEFORE FirstName — but the select list wins.
    //    Ask for the surname first, and the surname comes back first.
    def "you choose the column order, not the table"() {
        expect:
        shouldReturn([["Davolio", "Nancy"], ["Fuller", "Andrew"], ["Leverling", "Janet"]], '''
            SELECT ___, "FirstName" FROM "Employees"
        ''')
    }

    // 5) The other dial: the select list picks the COLUMNS, LIMIT picks how many ROWS.
    //    Products holds twenty. Fill in the number that keeps just the first two.
    def "limit the rows: only the first two products"() {
        expect:
        shouldReturn([["Chai"], ["Chang"]], '''
            SELECT "ProductName" FROM "Products" LIMIT ___
        ''')
    }

    // 6) UnitPrice is stored with four decimals. Show it with two instead — this changes
    //    the DISPLAY only; the price in the table is untouched.
    def "show a price with two decimals instead of four"() {
        expect:
        shouldReturn([["Chai", 18.00], ["Chang", 19.00], ["Aniseed Syrup", 10.00]], '''
            SELECT "ProductName", ROUND("UnitPrice", ___) FROM "Products" LIMIT 3
        ''')
    }

    // 7) The database describes itself. Note the two kinds of quotes:
    //    "Double" quotes name a COLUMN; 'single' quotes are a text VALUE.
    //    Fill in the value — the name of the column you're asking about.
    def "ask the catalog what type a column is"() {
        expect:
        shouldReturn "DECIMAL(10,4)", '''
            SELECT data_type FROM information_schema.columns
            WHERE table_name = 'Products' AND column_name = ___
        '''
    }

    // 8) The whole query — no scaffolding. Write the WHOLE query: first name, surname and title
    //    of every employee, in that column order.
    def "write it yourself: the staff list"() {
        expect:
        shouldReturn([["Nancy", "Davolio", "Sales Representative"],
                      ["Andrew", "Fuller", "Vice President, Sales"],
                      ["Janet", "Leverling", "Sales Representative"]], '''
            ___
        ''')
    }
}
