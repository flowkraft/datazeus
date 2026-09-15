package datazeus.datamodeling.series1._05

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Data Modeling · Series 1 · lesson 05
 * "Entities & Attributes — Is It a Thing, or a Fact About a Thing?".
 *
 * Every figure the video, the article, the trailer, the Short and the cards put in front of a
 * learner is asserted here, on BOTH engines. The lesson's seven scripts, in the order it runs them:
 *
 *    1. customer-contacts          — a name and a title riding on each customer             §2
 *    2. list-categories            — category has facts of its own: 8 names, 8 descriptions §1
 *    3. customers-per-country      — country repeats (Germany 11) yet has no facts           §1
 *    4. customer-contact-titles    — nine titles: a title is a fact about the PERSON         §2
 *    5. supplier-contact-titles    — the same kind of thing in a second table                §2
 *    6. who-handles-accounts       — the business's question finds 2 customers               §3
 *    7. packed-quantities          — three facts packed into one text                        §4
 *
 * §5 holds the scripts to the course's conventions. THIS LESSON HAS NO CHECKS (curriculum:
 * hands_on has no check:*), so there is no checks-file section to run — the spec says so in §6,
 * so that adding one to the checks file without a spec fails loudly.
 *
 * READ-ONLY: nothing is created, so the shared engines need no schema of their own here.
 */
class EntitiesAndAttributesSpec extends NorthwindGateSpec {

    // --- 1. Thing or fact: category and country --------------------------------------------

    @Unroll
    def "[#engine] every category has a name AND a description of its own — 8 of them"() {
        when:
        def rows = sqlFor(engine).rows(script("list-categories"))

        then:
        rows.size() == 8
        rows*.CategoryName == ["Beverages", "Condiments", "Confections", "Dairy Products",
                               "Grains/Cereals", "Meat/Poultry", "Produce", "Seafood"]
        rows.every { it.Description?.toString()?.trim() }
        rows[0].Description == "Soft drinks, coffees, teas, beers, and ales"
        rows[3].Description == "Cheeses"

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] 25 customers in 10 countries, Germany 11 — country repeats and is still only a name"() {
        when:
        def rows = sqlFor(engine).rows(script("customers-per-country"))

        then:
        rows.collect { [it.Country, it.Customers as int] } == [
                ["Germany", 11], ["France", 2], ["Mexico", 2], ["Sweden", 2], ["UK", 2],
                ["Venezuela", 2], ["Argentina", 1], ["Austria", 1], ["Italy", 1], ["USA", 1]]
        rows.sum { it.Customers as int } == 25
        and: "Northwind has no country table — no fact of its own is stored anywhere"
        tableCount(engine, "Countries") == 0

        where:
        engine << ENGINES
    }

    // --- 2. The contact: two columns that travel together -----------------------------------

    @Unroll
    def "[#engine] the contact rides on every customer: a name and a title, the first three as on screen"() {
        when:
        def rows = sqlFor(engine).rows(script("customer-contacts"))

        then:
        rows.collect { [it.CompanyName, it.ContactName, it.ContactTitle] } == [
                ["Alfreds Futterkiste", "Maria Anders", "Sales Representative"],
                ["Ana Trujillo Emparedados y helados", "Ana Trujillo", "Owner"],
                ["Antonio Moreno Taquería", "Antonio Moreno", "Owner"]]
        and: "every one of the 25 has both — one person per customer, never two"
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Customers"
                                   WHERE "ContactName" IS NOT NULL AND "ContactTitle" IS NOT NULL''').n == 25

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] ContactName and ContactTitle travel together on BOTH Customers and Suppliers"() {
        expect:
        ["Customers", "Suppliers"].every { t ->
            ["ContactName", "ContactTitle"].every { c -> hasColumn(engine, t, c) }
        }
        !hasColumn(engine, "Customers", "ContactName2")

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] nine contact titles over the customers, four over six supplier contacts"() {
        when:
        def customers = sqlFor(engine).rows(script("customer-contact-titles"))
        def suppliers = sqlFor(engine).rows(script("supplier-contact-titles"))

        then:
        customers.collect { [it.ContactTitle, it.Customers as int] } == [
                ["Owner", 6], ["Sales Representative", 6], ["Marketing Manager", 5],
                ["Accounting Manager", 2], ["Order Administrator", 2], ["Marketing Assistant", 1],
                ["Sales Agent", 1], ["Sales Associate", 1], ["Sales Manager", 1]]
        suppliers.collect { [it.ContactTitle, it.Suppliers as int] } == [
                ["Marketing Manager", 2], ["Order Administrator", 2],
                ["Purchasing Manager", 1], ["Sales Representative", 1]]
        suppliers.sum { it.Suppliers as int } == 6

        where:
        engine << ENGINES
    }

    // --- 3. The business's question -------------------------------------------------------

    @Unroll
    def "[#engine] who handles accounts? today's model can answer for 2 customers"() {
        expect:
        sqlFor(engine).rows(script("who-handles-accounts")).collect { [it.CompanyName, it.ContactName] } ==
                [["LILA-Supermercado", "Carlos Gonzalez"], ["QUICK-Stop", "Horst Kloss"]]

        where:
        engine << ENGINES
    }

    // --- 4. A packed attribute ------------------------------------------------------------

    @Unroll
    def "[#engine] quantity per unit packs a count, a size and a unit into one text"() {
        expect:
        sqlFor(engine).rows(script("packed-quantities")).collect { [it.ProductName, it.QuantityPerUnit] } == [
                ["Chai", "10 boxes x 20 bags"],
                ["Chang", "24 - 12 oz bottles"],
                ["Thuringer Rostbratwurst", "50 bags x 30 sausgs."],
                ["Queso Cabrales", "1 kg pkg."]]

        where:
        engine << ENGINES
    }

    // --- 5. The scripts keep the course's conventions -------------------------------------

    def "every script reads Northwind UNQUALIFIED, has no comments and no long middle line"() {
        given:
        def all = new File(SCRIPTS).listFiles().findAll { it.name.endsWith(".sql") }

        expect:
        all.size() == 7
        all.every { !(it.text =~ /(?i)\b(main|public)\./) }
        all.every { !it.text.contains("--") }
        all.every { f -> def l = f.text.readLines(); l.size() < 3 || l[1..-2].every { it.length() <= 45 } }
    }

    // --- 6. No checks for this episode -----------------------------------------------------

    def "the checks file has no Episode 05 section — this lesson is practised in the file and the cards"() {
        expect:
        !new File(CHECKS).text.contains("// ── Episode 05 ·")
    }

    // --- helpers --------------------------------------------------------------------------

    static final String SCRIPTS = "../courses/datamodeling/series1-modeling-fundamentals/05-entities-and-attributes/scripts"
    static final String CHECKS = "src/koans/groovy/datazeus/datamodeling/series1/NorthwindModelChecks.groovy"

    private static String script(String name) {
        new File("${SCRIPTS}/${name}.sql").text.trim().replaceFirst(/;\s*$/, "")
    }

    private boolean hasColumn(String engine, String table, String column) {
        (sqlFor(engine).firstRow('''SELECT count(*) AS n FROM information_schema.columns
                                    WHERE table_name = ? AND column_name = ?
                                      AND table_schema IN ('main', 'public')''', [table, column]).n as int) > 0
    }

    private int tableCount(String engine, String table) {
        sqlFor(engine).firstRow('''SELECT count(*) AS n FROM information_schema.tables
                                   WHERE table_name = ? AND table_schema IN ('main', 'public')''', [table]).n as int
    }
}
