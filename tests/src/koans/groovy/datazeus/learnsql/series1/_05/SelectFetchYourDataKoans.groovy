package datazeus.learnsql.series1._05

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 05 Meet Your Data With SELECT         ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Replace each `___`, then run
 *
 *     zeus.bat koans learnsql series1 _05  (Windows)   ./zeus.sh koans learnsql series1 _05  (macOS/Linux)
 *
 * Six koans, one per idea in the lesson and in the same order: ask for every column,
 * name one, name two, choose their order, limit the rows, then write a whole query yourself.
 *
 * These run on DuckDB. Every one of them returns the same rows in CloudBeaver against
 * PostgreSQL, so run them wherever you prefer.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class SelectFetchYourDataKoans extends KoanBase {

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

    // 4) The table stores LastName before FirstName — the select list wins. Write both
    //    names, surname first. Koan 3 gave you Nancy, Davolio; same columns, your order.
    def "you choose the column order, not the table"() {
        expect:
        shouldReturn([["Davolio", "Nancy"], ["Fuller", "Andrew"], ["Leverling", "Janet"]], '''
            SELECT ___ FROM "Employees"
        ''')
    }

    // 5) The other dial: the select list picks WHICH columns, LIMIT picks HOW MANY rows.
    //    Products holds twenty. Fill in the number that keeps just two of them.
    def "limit the rows: keep only two products"() {
        expect:
        shouldReturn([["Chai"], ["Chang"]], '''
            SELECT "ProductName" FROM "Products" LIMIT ___
        ''')
    }




    // 6) The whole query, no scaffolding — and it is the job this lesson opened with:
    //    somebody asked you to have a look at the products. Both dials at once: name the
    //    columns you want, and say how many rows you want back.
    //    Look things up if you need to — the lesson and your scripts folder are right there.
    //    This is recall with the book open, not an exam.
    def "write it yourself: have a look at the products"() {
        expect:
        shouldReturn([["Chai", 18.0000],
                      ["Chang", 19.0000],
                      ["Aniseed Syrup", 10.0000],
                      ["Chef Antons Cajun Seasoning", 22.0000],
                      ["Scottish Longbreads", 12.5000]], '''
            ___
        ''')
    }
}
