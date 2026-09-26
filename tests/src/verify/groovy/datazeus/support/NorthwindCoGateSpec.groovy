package datazeus.support

/**
 * Gate for every lesson on Northwind Company S (Learn SQL Series 2 onwards): the shipped
 * datasets/northwind-co/northwind_co.duckdb, schema northwind_co_s. Both engines are re-checksummed
 * against `_dataset_info` before any figure is asserted (see {@link GateEngines}).
 *
 *   PGHOST=localhost mvn test    expects Northwind Company installed in that PostgreSQL via Seed Data
 */
abstract class NorthwindCoGateSpec extends GateSpec {

    @Override
    protected String dataset() { "../datasets/northwind-co/northwind_co.duckdb" }

    @Override
    protected String schema() { "northwind_co_s" }
}
