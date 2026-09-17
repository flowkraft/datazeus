package datazeus._internal

/**
 * Koans on Northwind Company S (Learn SQL Series 2 onwards): the shipped
 * datasets/northwind-co/northwind_co.duckdb, schema northwind_co_s — the same rows the Seed Data
 * tab installs into CloudBeaver's PostgreSQL, so a koan's answer is the answer there too.
 */
abstract class NorthwindCoKoanBase extends KoanBase {

    @Override
    protected String dataset() { "../datasets/northwind-co/northwind_co.duckdb" }

    @Override
    protected String schema() { "northwind_co_s" }
}
