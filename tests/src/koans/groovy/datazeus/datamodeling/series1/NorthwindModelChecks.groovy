package datazeus.datamodeling.series1

import datazeus._internal.KoanHint
import datazeus._internal.SchemaKoanBase
import spock.lang.Stepwise

/**
 * ╔════════════════════════════════════════════════════════════════════════════════════════╗
 * ║  THE CHECKS — Data Modeling · Series 1 · Modeling Fundamentals
 * ╚════════════════════════════════════════════════════════════════════════════════════════╝
 *
 * WRITTEN EPISODE BY EPISODE. The sections for episodes 00, 15 and 20 are real, each asserted by its
 * episode's verify spec (StartHereSpec, GrainAndKeysSpec, ManyToManySpec) against the reference
 * solution on DuckDB and PostgreSQL. Episodes 05 and 10 add no checks (curriculum: diagram, working
 * file and cards). The sections from 25 on are still briefs.
 * The concrete class the runner picks up is NorthwindModelChecksKoans (the runner only includes
 * *Koans classes) — this file is the base it extends, so the name the curriculum uses stays.
 *
 *     zeus.bat koans datamodeling series1     (Windows)
 *     ./zeus.sh koans datamodeling series1    (macOS/Linux)
 *
 * ── WHAT THIS FILE IS, AND WHY IT IS NOT KOANS ──────────────────────────────────────────
 * Decided with the owner 2026-09-14. A modelling decision has more than one right answer, so
 * Data Modeling has no fill-in-the-blank koans. The learner builds their own model in their
 * working file; this ONE file checks it, the way a team's CI checks a schema. It grows episode
 * by episode, in order, so a learner at episode 20 sees everything up to 20 green and the rest
 * waiting. The judgement questions that used to be predict / diagnose / choose koans are Anki
 * cards now (cards/cards.yaml in each lesson).
 *
 * THIS SERIES' CHECKS: fits · refuses · answers
 *
 * ── THE PROPERTY TO PROTECT, ABOVE ALL ───────────────────────────────────────────────────
 * ANY MODEL THAT PASSES IS A CORRECT ANSWER, INCLUDING ONES WE DID NOT THINK OF. A check that
 * can only pass on OUR table names tests conformance, not modelling: rewrite it or drop it.
 * Name every check after the BUSINESS RULE ("that copy is already out with someone else"),
 * never after the constraint ("unique violation on loan_copy_idx").
 *
 * ── THE VIEWS THE LEARNER WRITES ─────────────────────────────────────────────────────────
 * schema.sql ends with these (Series 1 · 60 makes them complete; earlier episodes add the ones
 * they need). Everything behind them is the learner's own design.
 *
 *     practice.v_customer          (customer_id, company_name, country)
 *     practice.v_contact           (customer_id, contact_name, contact_role)      several per customer
 *     practice.v_order             (order_id, customer_id, employee_id, order_date,
 *                                   required_date, shipped_date, shipper_id, freight)
 *     practice.v_order_line        (order_id, product_id, unit_price, quantity, discount)
 *     practice.v_product_supplier  (product_id, supplier_id, supplier_price)      several per product
 *
 * The same views are Series 3's source, so they are an interface in the real sense: the star
 * is built on them, and the learner's tables can change behind them.
 *
 * MEASURED on the shipped northwind.duckdb, the numbers the checks assert:
 *   25 customers, 79 orders, 193 order lines, 20 products, 6 suppliers, 3 shippers, 3 employees,
 *   8 categories; order-line revenue sum(unit_price * quantity * (1 - discount)) = 58153.31.
 *
 * ── THE PINCER ───────────────────────────────────────────────────────────────────────────
 * Pair every "must refuse" with a "must still accept". A model that makes everything impossible
 * is not a good model, and a learner who only sees rejections learns to over-constrain.
 */
/*
 * ── HOW A CHECK IS WRITTEN (and how StartHereSpec and its successors read it) ──────────────
 * Every SQL a check runs is a ''' literal inside the check, in order: the earlier literals are
 * setup (`prepare(...)`), the LAST one is what is judged — by shouldReturn, shouldReject or
 * shouldAccept. The verify spec parses exactly that shape out of this file and runs it against
 * the reference solution on BOTH engines, so a check that only works on DuckDB, or only on our
 * table, fails the gate before a learner ever sees it. Keep the SQL portable: no main.,
 * no DuckDB-only syntax, TEMP probe tables dropped before they are created.
 *
 * ── HOW A PROBE STAYS FAIR TO ANY MODEL ──────────────────────────────────────────────────
 * A refusal or an accept COPIES A REAL ROW the learner's table already holds into a TEMP probe
 * table and changes only the column the rule is about. An INSERT with a hand-written column
 * list would bounce for the wrong reason the moment a learner adds a NOT NULL column of their
 * own — a false pass. The copy carries whatever columns their design has.
 */
@Stepwise // walk the checks in episode order — once one fails, the rest wait
abstract class NorthwindModelChecks extends SchemaKoanBase {

    /** The learner's working file, relative to the tests/ module the runner starts in. */
    protected String workingFile() { "../practice/schema.sql" }

    def setupSpec() {
        File mine = new File(workingFile())
        if (!mine.exists()) {
            throw new KoanHint("there is no working file yet. Make one with:\n" +
                    "  zeus practice reset\n" +
                    "then build your tables in practice/schema.sql and run the checks again.")
        }
        applySchema(withoutComments(mine.text))
    }

    /** Line comments out BEFORE the file is split on semicolons: a ";" inside a comment would
     *  otherwise cut a statement in half, and the template is mostly comments. */
    protected static String withoutComments(String sql) {
        sql.readLines().collect { it.replaceFirst(/--.*$/, "") }.join("\n")
    }

    /** A fits-check's first question, asked before its count: does the table exist at all? A
     *  learner who has not built it yet should be sent to THEIR file, not shown a catalog error
     *  that points at a line of this one. */
    protected boolean hasTable(String table) {
        long n = firstCell("SELECT count(*) FROM information_schema.tables WHERE table_schema = '${PRACTICE}' AND table_name = '${table}'".toString()) as long
        if (n == 0) {
            throw new KoanHint("your model has no practice.\"${table}\" table yet.\n" +
                    "build it in YOUR working file, practice/schema.sql, then run the checks again.\n" +
                    "(the line shown below is the check that is waiting, not the thing to edit)")
        }
        return true
    }

    /** Setup for one check: every statement in the literal, one at a time. */
    protected void prepare(String sql) {
        sql.split(";").findAll { it.trim() }.each { db.execute(it.trim()) }
    }

    // ── Episode 00 · Start Here: The Modeling Loop — fits, refuses
    //   the lesson builds practice."Categories"; the learner's turn is practice."Shippers" — a number
    //   no two shippers share, a name every shipper has — loaded from Northwind's 3 real rows.
    //   THE NAMES BELOW ARE ON SCREEN in the video's your-turn slide, verbatim. Rename both or neither.

    def "Northwind's 3 shippers fit your table"() {
        expect:
        hasTable("Shippers")
        shouldReturn 3, '''
            SELECT count(*) FROM practice."Shippers"
        '''
    }

    def "a second shipper number 1 is refused"() {
        expect:
        shouldReject '''
            INSERT INTO practice."Shippers"
            SELECT * FROM practice."Shippers" WHERE "ShipperID" = 1
        ''', "an order that says ShipVia 1 has to mean ONE company — two shippers numbered 1 make it mean either"
    }

    def "a shipper with no name is refused"() {
        given:
        prepare '''
            DROP TABLE IF EXISTS probe_shipper;
            CREATE TEMP TABLE probe_shipper AS
              SELECT * FROM practice."Shippers" WHERE "ShipperID" = 1;
            UPDATE probe_shipper SET "ShipperID" = 90, "CompanyName" = NULL
        '''

        expect:
        shouldReject '''
            INSERT INTO practice."Shippers" SELECT * FROM probe_shipper
        ''', "every shipper has a name — a delivery company nobody can name is not a shipper you can book"
    }

    def "a new shipper, number 4, still gets in"() {
        given:
        prepare '''
            DROP TABLE IF EXISTS probe_shipper;
            CREATE TEMP TABLE probe_shipper AS
              SELECT * FROM practice."Shippers" WHERE "ShipperID" = 1;
            UPDATE probe_shipper SET "ShipperID" = 4, "CompanyName" = 'Northwind Couriers'
        '''

        expect:
        shouldAccept '''
            INSERT INTO practice."Shippers" SELECT * FROM probe_shipper
        '''
    }

    // ── Episode 15 · Grain & Keys — fits, refuses
    //   the lesson keys practice."Order Details" by the pair after PRIMARY KEY ("OrderID") refuses the real rows;
    //   the learner's turn is the grain and a key for every other table too. Each fits-check loads the real
    //   rows through the learner's own key — a wrong grain refuses them, which is exactly what the check reports.
    //   FOUR OF THESE NAMES ARE ON SCREEN in the video's your-turn slide, verbatim. Rename both or neither.

    def "Northwind's 25 customers fit your table"() {
        expect:
        hasTable("Customers")
        shouldReturn 25, '''
            SELECT count(*) FROM practice."Customers"
        '''
    }

    def "Northwind's 79 orders fit your table"() {
        expect:
        hasTable("Orders")
        shouldReturn 79, '''
            SELECT count(*) FROM practice."Orders"
        '''
    }

    def "Northwind's 20 products fit your table"() {
        expect:
        hasTable("Products")
        shouldReturn 20, '''
            SELECT count(*) FROM practice."Products"
        '''
    }

    def "Northwind's 6 suppliers fit your table"() {
        expect:
        hasTable("Suppliers")
        shouldReturn 6, '''
            SELECT count(*) FROM practice."Suppliers"
        '''
    }

    def "Northwind's 3 employees fit your table"() {
        expect:
        hasTable("Employees")
        shouldReturn 3, '''
            SELECT count(*) FROM practice."Employees"
        '''
    }

    def "Northwind's 193 order lines fit your table"() {
        expect:
        hasTable("Order Details")
        shouldReturn 193, '''
            SELECT count(*) FROM practice."Order Details"
        '''
    }

    def "a second customer ALFKI is refused"() {
        expect:
        shouldReject '''
            INSERT INTO practice."Customers"
            SELECT * FROM practice."Customers" WHERE "CustomerID" = 'ALFKI'
        ''', "one code, one customer — an order for ALFKI has to mean one company, not whichever row the database finds first"
    }

    def "a second order number 1 is refused"() {
        expect:
        shouldReject '''
            INSERT INTO practice."Orders"
            SELECT * FROM practice."Orders" WHERE "OrderID" = 1
        ''', "one row per order — two orders numbered 1 make every line of order 1 ambiguous"
    }

    def "the same product twice on one order is refused"() {
        expect:
        shouldReject '''
            INSERT INTO practice."Order Details"
            SELECT * FROM practice."Order Details" WHERE "OrderID" = 1 AND "ProductID" = 1
        ''', "one row per product on an order — a second line for the same product double-counts it; raise the quantity instead"
    }

    def "a new customer NWCO1 still gets in"() {
        given:
        prepare '''
            DROP TABLE IF EXISTS probe_customer;
            CREATE TEMP TABLE probe_customer AS
              SELECT * FROM practice."Customers" WHERE "CustomerID" = 'ALFKI';
            UPDATE probe_customer SET "CustomerID" = 'NWCO1', "CompanyName" = 'Northwind Test Customer'
        '''

        expect:
        shouldAccept '''
            INSERT INTO practice."Customers" SELECT * FROM probe_customer
        '''
    }

    def "the same product on a different order still gets in"() {
        given:
        prepare '''
            DROP TABLE IF EXISTS probe_line;
            CREATE TEMP TABLE probe_line AS
              SELECT * FROM practice."Order Details" WHERE "OrderID" = 1 AND "ProductID" = 1;
            UPDATE probe_line SET "OrderID" = 2
        '''

        expect:
        shouldAccept '''
            INSERT INTO practice."Order Details" SELECT * FROM probe_line
        '''
    }


    // ── Episode 20 · Many-to-Many & Junction Tables — fits, answers
    //   the lesson resolves "a product from several suppliers, each at its own price" as
    //   practice."Product Suppliers", keyed by the pair and seeded with today's supplier of every product;
    //   the learner builds the same junction in their own file. Probes copy a real row and change only the
    //   supplier, so a learner's extra columns (their own price column name included) cannot fool them.
    //   FOUR OF THESE NAMES ARE ON SCREEN in the video's your-turn slide, verbatim. Rename both or neither.

    def "today's 20 product-supplier pairs fit your table"() {
        expect:
        hasTable("Product Suppliers")
        shouldReturn 20, '''
            SELECT count(*) FROM practice."Product Suppliers"
        '''
    }

    def "every one of the 20 products has a supplier in your table"() {
        expect:
        shouldReturn 20, '''
            SELECT count(DISTINCT "ProductID") FROM practice."Product Suppliers"
        '''
    }

    def "Tokyo Traders supplies 5 products in your table"() {
        expect:
        shouldReturn 5, '''
            SELECT count(*) FROM practice."Product Suppliers" WHERE "SupplierID" = 4
        '''
    }

    def "the same supplier twice for one product is refused"() {
        expect:
        shouldReject '''
            INSERT INTO practice."Product Suppliers"
            SELECT * FROM practice."Product Suppliers" WHERE "ProductID" = 1
        ''', "one price per product per supplier — two rows for the same pair make \"what does this supplier charge?\" ambiguous"
    }

    def "a second supplier for Chang still gets in"() {
        given:
        prepare '''
            DROP TABLE IF EXISTS probe_supply;
            CREATE TEMP TABLE probe_supply AS
              SELECT * FROM practice."Product Suppliers" WHERE "ProductID" = 2;
            UPDATE probe_supply SET "SupplierID" = 4
        '''

        expect:
        shouldAccept '''
            INSERT INTO practice."Product Suppliers" SELECT * FROM probe_supply
        '''
    }


    // ── Episode 25 · Normalization: 1NF, 2NF, 3NF — answers
    //   the learner's turn: build the flat table from your Learn SQL 50 report query, show one update anomaly on it, then decompose it.

    // ── Episode 30 · Copy or Reference — answers
    //   the learner's turn: decide copy or reference for the order-line price and the ship-to address, then change a product's price and confirm last year's revenue did not move.

    // ── Episode 40 · Data Types & Domains — fits
    //   the learner's turn: give every column a type you would defend; money must stay exact.

    // ── Episode 45 · Foreign Keys & Referential Integrity — refuses
    //   the learner's turn: declare every relationship from your diagram as a foreign key, parents first.
    //   Checks: an order for a customer that does not exist, a line for a product that does not
    //     exist — and the paired accepts. Referential actions (ON DELETE CASCADE / RESTRICT) shown
    //     in PostgreSQL, with the question that decides them: when the parent goes, what should
    //     happen to the history?

    // ── Episode 50 · NOT NULL, UNIQUE, CHECK & DEFAULT — fits, refuses
    //   the learner's turn: add NOT NULL, UNIQUE and CHECK only where Northwind's real rows agree.

    // ── Episode 52 · Nullable, Missing Row or Separate Table — fits
    //   the learner's turn: for every nullable column, one comment: optional value, fact not there yet, or a different kind of thing.

    // ── Episode 60 · Project — fits, refuses, answers
    //   the learner's turn: the whole model alone, ending schema.sql with the views the checks read.
    //   FITS counts through the views: 25 customers, 79 orders, 193 order lines, 20 products, 6
    //     suppliers, 3 shippers, 3 employees, 8 categories.
    //   REFUSES impossible rows bounce, each paired with a possible row that must still load.
    //   ANSWERS the Learn SQL 50 report over the views: total 58153.31, Cactus Comidas para llevar
    //     first.
    //   HOLDS a second contact for a customer and a second supplier for a product insert and read
    //     back.
}
