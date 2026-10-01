package datazeus._internal

/**
 * Koans on Northwind Company (Learn SQL Series 2 onwards): the shipped
 * datasets/northwind-co/northwind_co.duckdb, schema northwind_co_<scale> — the same rows the Seed Data
 * tab installs into CloudBeaver's PostgreSQL, so a koan's answer is the answer there too.
 *
 * The file holds scale S. A lesson on another scale overrides scale() ("m" or "l"); that schema must
 * then be installed into the file (academy-northwind-co-install.groovy, SCALE=M or L) before its koans run.
 */
abstract class NorthwindCoKoanBase extends KoanBase {

    /** Northwind Company's scale: "s", "m" or "l". */
    protected String scale() { "s" }

    @Override
    protected String dataset() { "../datasets/northwind-co/northwind_co.duckdb" }

    @Override
    protected String schema() { "northwind_co_" + scale() }
}
