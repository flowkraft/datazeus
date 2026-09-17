package datazeus.support

import groovy.sql.Sql
import spock.lang.Specification

/**
 * Base for the dual-engine PUBLISH GATE of every lesson on Northwind Company S (Learn SQL Series 2
 * onwards). Identical in use to {@link NorthwindGateSpec} — `where: engine << ENGINES` and
 * `sqlFor(engine)` — but both engines are on schema `northwind_co_s`, already proved to be the
 * dataset the lessons were measured on (see {@link NorthwindCoEngines}).
 *
 *   mvn test                     Docker: a throwaway PostgreSQL seeded from the DuckDB file
 *   PGHOST=localhost mvn test    the learner's PostgreSQL, with Northwind Company installed via Seed Data
 */
abstract class NorthwindCoGateSpec extends Specification {

    static final List<String> ENGINES = ["duckdb", "postgres"]

    protected Sql sqlFor(String engine) {
        engine == "postgres" ? NorthwindCoEngines.pg() : NorthwindCoEngines.duck()
    }
}
