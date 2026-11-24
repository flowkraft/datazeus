package datazeus.support

/**
 * Gate for every lesson on Northwind Company (Learn SQL Series 2 onwards): the shipped
 * datasets/northwind-co/northwind_co.duckdb, schema northwind_co_<scale> (S unless a lesson overrides
 * scale()). Both engines are re-checksummed against `_dataset_info` before any figure is asserted
 * (see {@link GateEngines}).
 *
 *   PGHOST=localhost mvn test    expects Northwind Company in that PostgreSQL's database northwind_co,
 *                                where DataPallas installs it when its Northwind PostgreSQL starts
 */
abstract class NorthwindCoGateSpec extends GateSpec {

    /** Northwind Company's scale: "s", "m" or "l". */
    protected String scale() { "s" }

    @Override
    protected String dataset() { "../datasets/northwind-co/northwind_co.duckdb" }

    @Override
    protected String schema() { "northwind_co_" + scale() }

    @Override
    protected String pgDatabase() { "northwind_co" }

    @Override
    protected List<String> duckdbOnlyTables() { [] }
}
