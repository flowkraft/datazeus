package datazeus.datamodeling.series1._10

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Data Modeling · Series 1 · lesson 10
 * "Relationships, Cardinality & Optionality — Business Rules as Lines on a Diagram".
 *
 * Every figure the video, the article, the trailer, the Short and the cards put in front of a
 * learner is asserted here, on BOTH engines. The lesson's seven scripts, in the order it runs them:
 *
 *    1. fewest-orders              — the data "says" every customer has orders: fewest is 2    §1
 *    2. customers-without-orders   — and not one customer has none                             §1
 *    3. products-per-supplier      — 3 · 2 · 3 · 5 · 3 · 4 today                                 §2
 *    4. unshipped-orders           — 27 not shipped, all 27 already name a shipper             §2
 *    5. reports-to                 — a line back to its own table; Andrew reports to nobody     §3
 *    6. products-on-order-1        — one order, many products                                    §4
 *    7. orders-with-chai           — one product, many orders                                    §4
 *
 * §5 holds diagram/northwind.puml — the PlantUML the video types and the article prints — to the
 * lesson's sentences, line by line, and to real tables. §6 the script conventions; §7 no checks.
 *
 * THE LESSON'S CLAIM THAT NORTHWIND DECLARES NO RELATIONSHIPS is asserted too (§5), on both engines:
 * if the dataset ever gains foreign keys, the "inferred, not declared" slide becomes false and this
 * spec fails first.
 *
 * READ-ONLY: nothing is created, so the shared engines need no schema of their own here.
 */
class RelationshipsCardinalitySpec extends NorthwindGateSpec {

    // --- 1. What the data says about the customer end ----------------------------------------

    @Unroll
    def "[#engine] the fewest orders any customer has is 2 — and no customer has none"() {
        expect:
        sqlFor(engine).rows(script("fewest-orders")).collect { [it.CustomerID, it.Orders as int] } ==
                [["QUICK", 2], ["TOMSP", 2], ["WANDK", 2]]
        (sqlFor(engine).firstRow(script("customers-without-orders")).values().first() as int) == 0
        and: "the most is 5, so 'between two and five' holds for every one of the 25"
        (sqlFor(engine).firstRow('''SELECT max(n) AS m FROM (SELECT count(*) AS n FROM "Orders" GROUP BY "CustomerID") t''').m as int) == 5
        (sqlFor(engine).firstRow('SELECT count(DISTINCT "CustomerID") AS n FROM "Orders"').n as int) == 25

        where:
        engine << ENGINES
    }

    // --- 2. Suppliers and the shipper ----------------------------------------------------------

    @Unroll
    def "[#engine] products per supplier are 3, 2, 3, 5, 3, 4 — every product has exactly one supplier"() {
        expect:
        sqlFor(engine).rows(script("products-per-supplier")).collect { [it.CompanyName, it.Products as int] } == [
                ["Exotic Liquids", 3], ["New Orleans Cajun Delights", 2], ["Grandma Kellys Homestead", 3],
                ["Tokyo Traders", 5], ["Pavlova Ltd", 3], ["Pasta Buttini s.r.l.", 4]]
        (sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Products" WHERE "SupplierID" IS NULL').n as int) == 0

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] 27 orders are unshipped and all 27 already name a shipper that exists"() {
        when:
        def row = sqlFor(engine).firstRow(script("unshipped-orders"))

        then:
        (row.Unshipped as int) == 27
        (row["With a shipper"] as int) == 27
        (sqlFor(engine).firstRow('''SELECT count(*) AS n FROM "Orders" o
                                   LEFT JOIN "Shippers" s ON s."ShipperID" = o."ShipVia"
                                   WHERE s."ShipperID" IS NULL''').n as int) == 0

        where:
        engine << ENGINES
    }

    // --- 3. The recursive line ---------------------------------------------------------------

    @Unroll
    def "[#engine] Nancy and Janet report to Andrew; Andrew reports to nobody"() {
        expect:
        sqlFor(engine).rows(script("reports-to")).collect { [it.Employee, it["Reports to"]] } ==
                [["Nancy", "Andrew"], ["Andrew", null], ["Janet", "Andrew"]]

        where:
        engine << ENGINES
    }

    // --- 4. Many at both ends ----------------------------------------------------------------

    @Unroll
    def "[#engine] order 1 holds products 1 and 6, and Chai sits on 10 orders"() {
        expect:
        sqlFor(engine).rows(script("products-on-order-1")).collect { [it.OrderID as int, it.ProductID as int] } == [[1, 1], [1, 6]]
        (sqlFor(engine).firstRow(script("orders-with-chai")).values().first() as int) == 10
        (sqlFor(engine).firstRow('SELECT "ProductName" AS n FROM "Products" WHERE "ProductID" = 1').n) == "Chai"

        where:
        engine << ENGINES
    }

    // --- 5. The diagram, as text --------------------------------------------------------------

    /** The relationship lines the lesson draws, and the sentence each one must say. */
    static final Map<String, String> LINES = [
            "Customer ||--o{ Order : places"               : "every order has exactly one customer; a customer has zero or many orders",
            "Supplier ||--o{ Product : supplies"           : "every product has exactly one supplier; a supplier has zero or many products",
            'Shipper |o--o{ Order : "ships via"'           : "an order has zero or one shipper; a shipper has zero or many orders",
            'Employee |o--o{ Employee : "reports to"'      : "an employee has zero or one manager; a manager has zero or many reports",
    ]

    def "the PlantUML file is a well-formed diagram whose lines are exactly the lesson's four sentences"() {
        given:
        def lines = new File(PUML).readLines()*.trim().findAll { it }
        def rel = lines.findAll { it =~ /--/ }
        def entities = lines.findAll { it.startsWith("entity ") }.collect { it - "entity " } as Set

        expect:
        lines.first() == "@startuml" && lines.last() == "@enduml"
        rel == LINES.keySet().toList()
        rel.every { it =~ /^(\w+) (\|\||\|o)--(o\{|\|\{|\|\||o\|) (\w+) : .+$/ }
        rel.every { l -> def m = (l =~ /^(\w+) \S+ (\w+)/); m.find() && entities.containsAll([m.group(1), m.group(2)]) }
    }

    def "the snippet typed on screen is in the file, and the deliberately wrong read-back line is not"() {
        expect: "the snippet typed on screen is the file's first relationship, verbatim"
        new File(PUML).text.contains("Customer ||--o{ Order : places")
        and: "the deliberately wrong read-back line differs from the file on exactly the supplier end"
        !new File(PUML).text.contains("Supplier ||--|{ Product")
    }

    def "the article prints the diagram file verbatim"() {
        expect:
        new File("${LESSON}/10-relationships-cardinality-optionality.mdx").text.contains(new File(PUML).text.trim())
    }

    @Unroll
    def "[#engine] every entity in the diagram is a real Northwind table"() {
        expect:
        ["Customers", "Orders", "Suppliers", "Products", "Shippers", "Employees"].every { tableExists(engine, it) }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] Northwind declares NO foreign keys — the diagram is inferred, as the lesson says"() {
        expect:
        (sqlFor(engine).firstRow('''SELECT count(*) AS n FROM information_schema.table_constraints
                                   WHERE constraint_type = 'FOREIGN KEY'
                                     AND table_schema IN ('main', 'public')''').n as int) == 0

        where:
        engine << ENGINES
    }

    // --- 6. The scripts keep the course's conventions -------------------------------------

    def "every script reads Northwind UNQUALIFIED, has no comments and no long middle line"() {
        given:
        def all = new File(SCRIPTS).listFiles().findAll { it.name.endsWith(".sql") }

        expect:
        all.size() == 7
        all.every { !(it.text =~ /(?i)\b(main|public)\./) }
        all.every { !it.text.contains("--") }
        all.every { f -> def l = f.text.readLines(); l.size() < 3 || l[1..-2].every { it.length() <= 45 } }
    }

    // --- 7. No checks for this episode -----------------------------------------------------

    def "the checks file has no Episode 10 section — this lesson is practised in the diagram and the cards"() {
        expect:
        !new File(CHECKS).text.contains("// ── Episode 10 ·")
    }

    // --- helpers --------------------------------------------------------------------------

    static final String LESSON = "../courses/datamodeling/series1-modeling-fundamentals/10-relationships-cardinality-optionality"
    static final String SCRIPTS = "${LESSON}/scripts"
    static final String PUML = "${LESSON}/diagram/northwind.puml"
    static final String CHECKS = "src/koans/groovy/datazeus/datamodeling/series1/NorthwindModelChecks.groovy"

    private static String script(String name) {
        new File("${SCRIPTS}/${name}.sql").text.trim().replaceFirst(/;\s*$/, "")
    }

    private boolean tableExists(String engine, String table) {
        (sqlFor(engine).firstRow('''SELECT count(*) AS n FROM information_schema.tables
                                    WHERE table_name = ? AND table_schema IN ('main', 'public')''', [table]).n as int) == 1
    }
}
